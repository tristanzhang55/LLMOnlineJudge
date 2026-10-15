import { useEffect, useRef, useState } from "react"
import { useTranslation } from "react-i18next"
import type { TFunction } from "i18next"

import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { ApiError } from "@/lib/api/client"
import {
  confirmRosterImport,
  countPersistResults,
  countPreviewRows,
  downloadRosterTemplate,
  isRosterXlsx,
  PERSIST_RESULT_BADGE_VARIANT,
  PREVIEW_STATUS_BADGE_VARIANT,
  previewRosterImport,
  rejectedRowsToTsv,
  ROSTER_CONFIRM_MAX_ROWS,
  ROSTER_FIELD_MAX,
  ROSTER_MAX_FILE_BYTES,
  ROSTER_TABLE_PAGE_SIZE,
  toConfirmRows,
  type RosterConfirmRow,
  type RosterPreviewRow,
  type RosterRowResult,
} from "@/lib/api/teacher-roster"
import { cn } from "@/lib/utils"

type ImportPhase = "idle" | "preview" | "results"

interface RosterImportPanelProps {
  onForbidden: () => void
  onRosterChanged?: () => void
}

function isAbortError(err: unknown): boolean {
  return err instanceof DOMException && err.name === "AbortError"
}

function pageCount(total: number): number {
  return Math.max(1, Math.ceil(total / ROSTER_TABLE_PAGE_SIZE))
}

function formatPreviewBanner(
  rows: RosterPreviewRow[],
  t: TFunction,
): string {
  const { total, counts } = countPreviewRows(rows)
  const parts: string[] = [t("roster:import.previewSummary.total", { count: total })]
  if (counts.valid) parts.push(t("roster:import.previewSummary.valid", { count: counts.valid }))
  if (counts.warning) parts.push(t("roster:import.previewSummary.warning", { count: counts.warning }))
  if (counts.error) parts.push(t("roster:import.previewSummary.error", { count: counts.error }))
  if (counts.already_imported) {
    parts.push(t("roster:import.previewSummary.alreadyImported", { count: counts.already_imported }))
  }
  return parts.join(t("listSep"))
}

function formatPersistBanner(
  results: RosterRowResult[],
  t: TFunction,
): string {
  const counts = countPersistResults(results)
  const rest: string[] = []
  if (counts.enrolled) rest.push(t("roster:import.persistSummary.enrolled", { count: counts.enrolled }))
  if (counts.already_imported) {
    rest.push(t("roster:import.persistSummary.alreadyImported", { count: counts.already_imported }))
  }
  if (counts.rejected) rest.push(t("roster:import.persistSummary.rejected", { count: counts.rejected }))
  const summary = t("roster:import.persistSummary.created", { count: counts.created })
  if (results.length > 0 && counts.created === results.length) return summary
  if (counts.created > 0 && rest.length === 0) return summary
  if (counts.created > 0) return `${summary}${t("listSep")}${rest.join(t("listSep"))}.`
  return rest.length ? `${rest.join(t("listSep"))}.` : ""
}

export function RosterImportPanel({ onForbidden, onRosterChanged }: RosterImportPanelProps) {
  const { t } = useTranslation()
  const fileInputRef = useRef<HTMLInputElement>(null)
  const previewAbortRef = useRef<AbortController | null>(null)
  const confirmAbortRef = useRef<AbortController | null>(null)
  const copyTimeoutRef = useRef<number | null>(null)

  const [phase, setPhase] = useState<ImportPhase>("idle")
  const [fileName, setFileName] = useState<string | null>(null)
  const [rows, setRows] = useState<RosterPreviewRow[]>([])
  const [results, setResults] = useState<RosterRowResult[]>([])
  const [sentRows, setSentRows] = useState<RosterConfirmRow[]>([])
  const [page, setPage] = useState(1)
  const [banner, setBanner] = useState<string | null>(null)
  const [bannerTone, setBannerTone] = useState<"default" | "destructive">("default")
  const [downloading, setDownloading] = useState(false)
  const [previewing, setPreviewing] = useState(false)
  const [confirmOpen, setConfirmOpen] = useState(false)
  const [confirming, setConfirming] = useState(false)
  const [copied, setCopied] = useState(false)

  useEffect(() => {
    return () => {
      previewAbortRef.current?.abort()
      confirmAbortRef.current?.abort()
      if (copyTimeoutRef.current != null) {
        window.clearTimeout(copyTimeoutRef.current)
      }
    }
  }, [])

  function handleForbiddenOrThrow(err: unknown): boolean {
    if (err instanceof ApiError && err.status === 403) {
      onForbidden()
      return true
    }
    return false
  }

  function resetToIdle() {
    previewAbortRef.current?.abort()
    confirmAbortRef.current?.abort()
    setPhase("idle")
    setFileName(null)
    setRows([])
    setResults([])
    setSentRows([])
    setPage(1)
    setBanner(null)
    setBannerTone("default")
    setPreviewing(false)
    setConfirming(false)
    setConfirmOpen(false)
    setCopied(false)
    if (fileInputRef.current) {
      fileInputRef.current.value = ""
    }
  }

  async function handleDownloadTemplate() {
    setDownloading(true)
    setBanner(null)
    try {
      await downloadRosterTemplate()
    } catch (err) {
      if (handleForbiddenOrThrow(err)) {
        return
      }
      const message = err instanceof ApiError ? err.message : t("roster:import.templateFailed")
      setBannerTone("destructive")
      setBanner(message || t("roster:import.templateFailed"))
    } finally {
      setDownloading(false)
    }
  }

  async function handleFileChange(file: File | undefined) {
    if (!file) {
      return
    }

    if (!isRosterXlsx(file)) {
      setPhase("idle")
      setFileName(null)
      setRows([])
      setBannerTone("destructive")
      setBanner(t("roster:import.xlsxOnly"))
      if (fileInputRef.current) {
        fileInputRef.current.value = ""
      }
      return
    }

    if (file.size > ROSTER_MAX_FILE_BYTES) {
      setPhase("idle")
      setFileName(null)
      setRows([])
      setBannerTone("destructive")
      setBanner(t("roster:import.tooLarge"))
      if (fileInputRef.current) {
        fileInputRef.current.value = ""
      }
      return
    }

    previewAbortRef.current?.abort()
    const controller = new AbortController()
    previewAbortRef.current = controller

    setPreviewing(true)
    setBanner(null)
    setFileName(file.name)
    setResults([])
    setSentRows([])
    setCopied(false)

    try {
      const parsed = await previewRosterImport(file, controller.signal)
      if (controller.signal.aborted) {
        return
      }
      setRows(parsed)
      setPage(1)
      setPhase("preview")
      if (parsed.length > ROSTER_CONFIRM_MAX_ROWS) {
        setBannerTone("destructive")
        setBanner(t("roster:import.tooManyRows", { count: ROSTER_CONFIRM_MAX_ROWS }))
      }
    } catch (err) {
      if (controller.signal.aborted || isAbortError(err)) {
        return
      }
      if (handleForbiddenOrThrow(err)) {
        return
      }
      setPhase("idle")
      setRows([])
      setFileName(null)
      setBannerTone("destructive")
      setBanner(err instanceof ApiError ? err.message : t("roster:import.parseFailed"))
      if (fileInputRef.current) {
        fileInputRef.current.value = ""
      }
    } finally {
      if (!controller.signal.aborted) {
        setPreviewing(false)
      }
    }
  }

  function updateRow<K extends keyof RosterPreviewRow>(index: number, key: K, value: RosterPreviewRow[K]) {
    setRows((current) => {
      const next = [...current]
      const row = next[index]
      if (!row) {
        return current
      }
      next[index] = { ...row, [key]: value }
      return next
    })
  }

  const canConfirm =
    phase === "preview" &&
    rows.length >= 1 &&
    rows.length <= ROSTER_CONFIRM_MAX_ROWS &&
    !previewing &&
    !confirming

  async function handleConfirm() {
    confirmAbortRef.current?.abort()
    const controller = new AbortController()
    confirmAbortRef.current = controller
    const payload = toConfirmRows(rows)
    setConfirming(true)
    setBanner(null)

    try {
      const nextResults = await confirmRosterImport(payload, controller.signal)
      if (controller.signal.aborted) {
        return
      }
      setSentRows(payload)
      setResults(nextResults)
      setPage(1)
      setPhase("results")
      setConfirmOpen(false)
      setBannerTone("default")
      setBanner(formatPersistBanner(nextResults, t) || null)
      onRosterChanged?.()
    } catch (err) {
      if (controller.signal.aborted || isAbortError(err)) {
        return
      }
      if (handleForbiddenOrThrow(err)) {
        return
      }
      setConfirmOpen(false)
      setBannerTone("destructive")
      setBanner(err instanceof ApiError ? err.message : t("roster:import.importFailed"))
    } finally {
      if (!controller.signal.aborted) {
        setConfirming(false)
      }
    }
  }

  async function handleCopyRejected() {
    const tsv = rejectedRowsToTsv(sentRows, results)
    if (!tsv) {
      return
    }
    try {
      await navigator.clipboard.writeText(tsv)
      setCopied(true)
      if (copyTimeoutRef.current != null) {
        window.clearTimeout(copyTimeoutRef.current)
      }
      copyTimeoutRef.current = window.setTimeout(() => setCopied(false), 2000)
    } catch {
      setBannerTone("destructive")
      setBanner(t("roster:import.copyFailed"))
    }
  }

  const previewPages = pageCount(rows.length)
  const resultPages = pageCount(results.length)
  const previewSlice = rows.slice(
    (page - 1) * ROSTER_TABLE_PAGE_SIZE,
    page * ROSTER_TABLE_PAGE_SIZE,
  )
  const resultSlice = results.slice(
    (page - 1) * ROSTER_TABLE_PAGE_SIZE,
    page * ROSTER_TABLE_PAGE_SIZE,
  )
  const rejectedCount = results.filter((item) => item.result === "rejected").length
  const currentPages = phase === "results" ? resultPages : previewPages
  const currentTotal = phase === "results" ? results.length : rows.length

  return (
    <div className="space-y-4">
      <p className="text-sm text-muted-foreground">
        {t("roster:import.intro")}
      </p>
      <div className="flex flex-wrap items-center gap-2">
        <Button type="button" variant="outline" disabled={downloading} onClick={() => void handleDownloadTemplate()}>
          {downloading ? t("roster:import.downloading") : t("roster:import.downloadTemplate")}
        </Button>
        {phase === "idle" || previewing ? (
          <>
            <input
              ref={fileInputRef}
              type="file"
              accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
              className="hidden"
              disabled={previewing}
              onChange={(event) => {
                const file = event.target.files?.[0]
                void handleFileChange(file)
              }}
            />
            <Button
              type="button"
              disabled={previewing}
              onClick={() => fileInputRef.current?.click()}
            >
              {t("roster:import.pickExcel")}
            </Button>
            {fileName ? <span className="text-sm text-muted-foreground">{fileName}</span> : null}
          </>
        ) : null}
        {phase === "preview" ? (
          <>
            {fileName ? <span className="text-sm text-muted-foreground">{fileName}</span> : null}
            <Button type="button" disabled={!canConfirm} onClick={() => setConfirmOpen(true)}>
              {t("roster:import.confirmImport")}
            </Button>
            <Button type="button" variant="outline" onClick={resetToIdle}>
              {t("roster:import.reupload")}
            </Button>
          </>
        ) : null}
        {phase === "results" ? (
          <>
            <Button type="button" disabled={rejectedCount === 0} onClick={() => void handleCopyRejected()}>
              {copied ? t("roster:import.copied") : t("roster:import.copyRejected")}
            </Button>
            <Button type="button" variant="outline" onClick={resetToIdle}>
              {t("roster:import.reupload")}
            </Button>
          </>
        ) : null}
      </div>

      {phase === "idle" || phase === "preview" ? (
        <div className="space-y-1 text-sm text-muted-foreground">
          <p>{t("roster:import.columns")}</p>
          <p>{t("roster:import.columnOrder")}</p>
          <p>{t("roster:import.studentIdRules")}</p>
          <p>
            {t("roster:import.previewHint")}
          </p>
        </div>
      ) : null}

      {phase === "preview" && rows.length > 0 ? (
        <div className="space-y-1">
          <p className="text-sm">{formatPreviewBanner(rows, t)}</p>
          <p className="text-sm text-muted-foreground">{t("roster:import.editHint")}</p>
        </div>
      ) : null}

      {banner ? (
        <p className={cn("text-sm", bannerTone === "destructive" && "text-destructive")}>{banner}</p>
      ) : null}

      {previewing ? (
        <div className="py-16 text-center text-muted-foreground">{t("roster:import.parsing")}</div>
      ) : null}

      {phase === "preview" && !previewing && rows.length === 0 ? (
        <div className="py-16 text-center text-muted-foreground">{t("roster:import.noRows")}</div>
      ) : null}

      {phase === "preview" && !previewing && rows.length > 0 ? (
        <>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t("roster:import.rowIndex")}</TableHead>
                <TableHead>{t("roster:import.studentId")}</TableHead>
                <TableHead>{t("roster:import.name")}</TableHead>
                <TableHead>{t("roster:import.major")}</TableHead>
                <TableHead>{t("roster:import.class")}</TableHead>
                <TableHead>{t("roster:import.semester")}</TableHead>
                <TableHead>{t("roster:import.initialPassword")}</TableHead>
                <TableHead>{t("roster:import.status")}</TableHead>
                <TableHead>{t("roster:import.notes")}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {previewSlice.map((row, sliceIndex) => {
                const index = (page - 1) * ROSTER_TABLE_PAGE_SIZE + sliceIndex
                return (
                  <TableRow
                    key={`${row.rowIndex}-${index}`}
                    className={row.status === "error" ? "bg-destructive/5" : undefined}
                  >
                    <TableCell>{row.rowIndex}</TableCell>
                    <TableCell>
                      <Input
                        value={row.studentId}
                        maxLength={ROSTER_FIELD_MAX.studentId}
                        inputMode="numeric"
                        onChange={(event) => updateRow(index, "studentId", event.target.value)}
                      />
                    </TableCell>
                    <TableCell>
                      <Input
                        value={row.studentName}
                        maxLength={ROSTER_FIELD_MAX.studentName}
                        onChange={(event) => updateRow(index, "studentName", event.target.value)}
                      />
                    </TableCell>
                    <TableCell>
                      <Input
                        value={row.major}
                        maxLength={ROSTER_FIELD_MAX.major}
                        onChange={(event) => updateRow(index, "major", event.target.value)}
                      />
                    </TableCell>
                    <TableCell>
                      <Input
                        value={row.className}
                        maxLength={ROSTER_FIELD_MAX.className}
                        onChange={(event) => updateRow(index, "className", event.target.value)}
                      />
                    </TableCell>
                    <TableCell>
                      <Input
                        value={row.semester}
                        maxLength={ROSTER_FIELD_MAX.semester}
                        onChange={(event) => updateRow(index, "semester", event.target.value)}
                      />
                    </TableCell>
                    <TableCell className="min-w-[8rem]">
                      <Input
                        type="text"
                        value={row.initialPassword ?? ""}
                        maxLength={ROSTER_FIELD_MAX.initialPassword}
                        autoComplete="off"
                        onChange={(event) => updateRow(index, "initialPassword", event.target.value)}
                      />
                    </TableCell>
                    <TableCell>
                      <Badge variant={PREVIEW_STATUS_BADGE_VARIANT[row.status]}>
                        {t(`roster:import.previewStatus.${row.status}`)}
                      </Badge>
                    </TableCell>
                    <TableCell className="min-w-[16rem] whitespace-normal">
                      {row.messages.join(t("listSep"))}
                    </TableCell>
                  </TableRow>
                )
              })}
            </TableBody>
          </Table>
        </>
      ) : null}

      {phase === "results" ? (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>{t("roster:import.rowIndex")}</TableHead>
              <TableHead>{t("roster:import.studentId")}</TableHead>
              <TableHead>{t("roster:import.name")}</TableHead>
              <TableHead>{t("roster:import.result")}</TableHead>
              <TableHead>{t("roster:import.notes")}</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {resultSlice.map((item, sliceIndex) => {
              const index = (page - 1) * ROSTER_TABLE_PAGE_SIZE + sliceIndex
              return (
                <TableRow
                  key={`${item.rowIndex}-${index}`}
                  className={item.result === "rejected" ? "bg-destructive/5" : undefined}
                >
                  <TableCell>{item.rowIndex}</TableCell>
                  <TableCell>{item.studentId}</TableCell>
                  <TableCell>{sentRows[index]?.studentName ?? ""}</TableCell>
                  <TableCell>
                    <Badge variant={PERSIST_RESULT_BADGE_VARIANT[item.result]}>
                      {t(`roster:import.persistResult.${item.result}`)}
                    </Badge>
                  </TableCell>
                  <TableCell className="min-w-[16rem] whitespace-normal">
                    {item.messages.join(t("listSep"))}
                  </TableCell>
                </TableRow>
              )
            })}
          </TableBody>
        </Table>
      ) : null}

      {(phase === "preview" && rows.length > 0) || phase === "results" ? (
        <div className="flex flex-wrap items-center justify-between gap-4">
          <p className="text-sm text-muted-foreground">
            {t("roster:import.pageSummary", { page, totalPages: currentPages, total: currentTotal })}
          </p>
          <div className="flex items-center gap-1">
            <Button
              type="button"
              variant="outline"
              size="sm"
              disabled={page <= 1}
              onClick={() => setPage((current) => Math.max(1, current - 1))}
            >
              {t("prevPage")}
            </Button>
            <Button
              type="button"
              variant="outline"
              size="sm"
              disabled={page >= currentPages}
              onClick={() => setPage((current) => Math.min(currentPages, current + 1))}
            >
              {t("nextPage")}
            </Button>
          </div>
        </div>
      ) : null}

      <AlertDialog
        open={confirmOpen}
        onOpenChange={(open) => {
          if (!confirming) {
            setConfirmOpen(open)
          }
        }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t("roster:import.confirmTitle")}</AlertDialogTitle>
            <AlertDialogDescription>
              {t("roster:import.confirmBody", { count: rows.length })}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={confirming}>{t("cancel")}</AlertDialogCancel>
            <AlertDialogAction
              type="button"
              disabled={confirming}
              onClick={(event) => {
                event.preventDefault()
                void handleConfirm()
              }}
            >
              {confirming ? t("roster:import.importing") : t("roster:import.confirmImport")}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  )
}
