import { useCallback, useEffect, useState } from "react"
import type { TFunction } from "i18next"
import { useTranslation } from "react-i18next"
import { Link, useNavigate, useSearchParams } from "react-router-dom"
import { Search } from "lucide-react"

import { Badge } from "@/components/ui/badge"
import { Button, buttonVariants } from "@/components/ui/button"
import { Checkbox } from "@/components/ui/checkbox"
import { Input } from "@/components/ui/input"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { fetchClasses, formatClassLabel, type ClassOption } from "@/lib/api/classes"
import { ApiError } from "@/lib/api/client"
import {
  countForwardResults,
  DISPUTE_LIST_STATUS_BADGE_CLASS,
  FORWARD_BATCH_MAX,
  fetchTeacherDisputes,
  teacherDisputeDetailPath,
  teacherDisputeDetailState,
  type ForwardDisputeResult,
  type PaginatedTeacherDisputes,
} from "@/lib/api/teacher-disputes"
import {
  ROUND_STATUS_BADGE_CLASS,
} from "@/lib/api/teacher-submissions"
import { formatDeadlineDisplay, PAGE_SIZE } from "@/lib/mock-assignments"
import { cn } from "@/lib/utils"

import { ForwardDisputesDialog } from "@/pages/disputes/ForwardDisputesDialog"

function parseDisputeStatus(value: string | null): 1 | 2 {
  return value === "2" ? 2 : 1
}

function formatForwardBanner(
  results: ForwardDisputeResult[],
  t: TFunction,
): string {
  const counts = countForwardResults(results)
  const rest: string[] = []
  if (counts.already_resolved) {
    rest.push(t("disputes:forward.alreadyResolved", { count: counts.already_resolved }))
  }
  if (counts.already_withdrawn) {
    rest.push(t("disputes:forward.alreadyWithdrawn", { count: counts.already_withdrawn }))
  }
  if (counts.not_found) {
    rest.push(t("disputes:forward.notFound", { count: counts.not_found }))
  }
  if (counts.conflict) {
    rest.push(t("disputes:forward.conflict", { count: counts.conflict }))
  }
  const summary = t("disputes:forward.summary", { count: counts.forwarded })
  if (results.length > 0 && counts.forwarded === results.length) {
    return summary
  }
  if (counts.forwarded > 0 && rest.length === 0) {
    return summary
  }
  if (counts.forwarded > 0) {
    return `${summary}.${rest.join(t("listSep"))}.`
  }
  return rest.length ? `${rest.join(t("listSep"))}.` : ""
}

export function DisputeList() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const page = Math.max(1, Number(searchParams.get("page") ?? "1") || 1)
  const disputeStatus = parseDisputeStatus(searchParams.get("disputeStatus"))

  const [inputValue, setInputValue] = useState("")
  const [studentName, setStudentName] = useState("")
  const [classFilter, setClassFilter] = useState("all")
  const [data, setData] = useState<PaginatedTeacherDisputes | null>(null)
  const [classes, setClasses] = useState<ClassOption[]>([])
  const [classesError, setClassesError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [forbidden, setForbidden] = useState(false)
  const [reloadKey, setReloadKey] = useState(0)
  const [selectedIds, setSelectedIds] = useState<Set<number>>(() => new Set())
  const [forwardOpen, setForwardOpen] = useState(false)
  const [forwardBanner, setForwardBanner] = useState<string | null>(null)

  const classId = classFilter === "all" ? null : Number(classFilter)
  const hasFilters = Boolean(studentName.trim()) || classId != null

  function writeListParams(nextPage: number, status: 1 | 2) {
    const next = new URLSearchParams()
    next.set("page", String(nextPage))
    if (status === 2) {
      next.set("disputeStatus", "2")
    }
    setSearchParams(next)
  }

  const loadClasses = useCallback(async (signal?: AbortSignal) => {
    setClassesError(null)
    try {
      const classList = await fetchClasses(signal)
      if (signal?.aborted) {
        return
      }
      setClasses(classList)
    } catch (err) {
      if (signal?.aborted || (err instanceof DOMException && err.name === "AbortError")) {
        return
      }
      setClasses([])
      setClassesError(t("disputes:list.classesFailed"))
    }
  }, [])

  const loadDisputes = useCallback(
    async (signal?: AbortSignal) => {
      setLoading(true)
      setError(null)
      setForbidden(false)

      try {
        const result = await fetchTeacherDisputes(
          {
            page,
            pageSize: PAGE_SIZE,
            studentName,
            classId: classId != null && Number.isFinite(classId) && classId > 0 ? classId : null,
            disputeStatus,
          },
          signal,
        )
        if (signal?.aborted) {
          return
        }

        if (result.items.length === 0 && result.total > 0 && page > 1) {
          writeListParams(1, disputeStatus)
          return
        }

        setData(result)
      } catch (err) {
        if (signal?.aborted || (err instanceof DOMException && err.name === "AbortError")) {
          return
        }
        if (err instanceof ApiError && err.status === 403) {
          setForbidden(true)
          setData(null)
          return
        }
        if (err instanceof ApiError) {
          setError(err.message)
        } else {
          setError(t("disputes:list.loadFailed"))
        }
        setData(null)
      } finally {
        if (!signal?.aborted) {
          setLoading(false)
        }
      }
    },
    [page, studentName, classId, disputeStatus],
  )

  useEffect(() => {
    const controller = new AbortController()
    void loadClasses(controller.signal)
    return () => controller.abort()
  }, [loadClasses, reloadKey])

  useEffect(() => {
    const controller = new AbortController()
    void loadDisputes(controller.signal)
    return () => controller.abort()
  }, [loadDisputes, reloadKey])

  useEffect(() => {
    setSelectedIds(new Set())
  }, [page, disputeStatus, studentName, classFilter])

  useEffect(() => {
    setForwardBanner(null)
  }, [disputeStatus, studentName, classFilter])

  function handleSearch() {
    setStudentName(inputValue.trim())
    writeListParams(1, disputeStatus)
  }

  function handleReset() {
    setInputValue("")
    setStudentName("")
    setClassFilter("all")
    navigate(
      disputeStatus === 2 ? "/teacher/disputes?disputeStatus=2" : "/teacher/disputes",
      { replace: true },
    )
  }

  function handleClassFilterChange(nextValue: string | null) {
    setClassFilter(nextValue ?? "all")
    writeListParams(1, disputeStatus)
  }

  const pageItems = data?.items ?? []
  const selectedCount = selectedIds.size
  const overCap = selectedCount > FORWARD_BATCH_MAX
  const allPageSelected =
    disputeStatus === 1 &&
    pageItems.length > 0 &&
    pageItems.every((item) => selectedIds.has(item.id))
  const hasManualOverride = pageItems.some(
    (item) => selectedIds.has(item.id) && item.isManualOverride,
  )

  function toggleSelected(id: number, checked: boolean) {
    setSelectedIds((current) => {
      const next = new Set(current)
      if (checked) {
        next.add(id)
      } else {
        next.delete(id)
      }
      return next
    })
  }

  function togglePage(checked: boolean) {
    setSelectedIds((current) => {
      const next = new Set(current)
      for (const item of pageItems) {
        if (checked) {
          next.add(item.id)
        } else {
          next.delete(item.id)
        }
      }
      return next
    })
  }

  async function handleForwarded(results: ForwardDisputeResult[]) {
    const banner = formatForwardBanner(results, t)
    setForwardBanner(banner || null)
    setSelectedIds(new Set())
    setReloadKey((key) => key + 1)
  }

  if (forbidden) {
    return (
      <div className="flex flex-col items-center justify-center gap-4 py-16 text-center">
        <p className="text-muted-foreground">{t("forbidden")}</p>
        <Link to="/teacher" className={cn(buttonVariants({ variant: "outline" }))}>
          {t("backHome")}
        </Link>
      </div>
    )
  }

  const pageNumbers = data ? Array.from({ length: data.totalPages }, (_, index) => index + 1) : []
  const isEmpty = !loading && !error && data?.total === 0

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center gap-2">
        <Button
          type="button"
          variant={disputeStatus === 1 ? "secondary" : "outline"}
          onClick={() => writeListParams(1, 1)}
        >
          {t("disputes:list.open")}
        </Button>
        <Button
          type="button"
          variant={disputeStatus === 2 ? "secondary" : "outline"}
          onClick={() => writeListParams(1, 2)}
        >
          {t("disputes:list.handled")}
        </Button>
        {disputeStatus === 1 ? (
          <>
            <span className="text-sm text-muted-foreground">{t("disputes:list.selected", { count: selectedCount })}</span>
            <Button
              type="button"
              disabled={selectedCount === 0 || overCap}
              onClick={() => setForwardOpen(true)}
            >
              {t("disputes:list.forwardAi")}
            </Button>
            {overCap ? <span className="text-sm text-destructive">{t("disputes:list.overCap")}</span> : null}
          </>
        ) : null}
        <div className="relative w-[280px]">
          <Search className="pointer-events-none absolute top-1/2 left-2.5 size-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            value={inputValue}
            onChange={(event) => setInputValue(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === "Enter") {
                handleSearch()
              }
            }}
            placeholder={t("disputes:list.searchPlaceholder")}
            autoComplete="off"
            className="pl-8"
          />
        </div>
        <Button type="button" variant="outline" onClick={handleSearch}>
          {t("search")}
        </Button>
        <Button type="button" variant="outline" onClick={handleReset}>
          {t("reset")}
        </Button>
        <Select value={classFilter} onValueChange={handleClassFilterChange}>
          <SelectTrigger className="w-[180px]">
            <SelectValue placeholder={t("allClasses")} />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">{t("allClasses")}</SelectItem>
            {classes.map((item) => (
              <SelectItem key={item.id} value={String(item.id)}>
                {formatClassLabel(item)}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {classesError ? (
        <p className="text-sm text-muted-foreground">{classesError}</p>
      ) : null}
      {forwardBanner ? <p className="text-sm">{forwardBanner}</p> : null}

      {loading ? (
        <div className="py-16 text-center text-muted-foreground">{t("loading")}</div>
      ) : error ? (
        <div className="flex flex-col items-center justify-center gap-4 py-16 text-center">
          <p className="text-destructive">{error}</p>
          <Button type="button" variant="outline" onClick={() => setReloadKey((key) => key + 1)}>
            {t("retry")}
          </Button>
        </div>
      ) : isEmpty ? (
        <div className="py-16 text-center text-muted-foreground">
          {hasFilters
            ? t("disputes:list.noMatch")
            : disputeStatus === 2
              ? t("disputes:list.emptyHandled")
              : t("disputes:list.emptyOpen")}
        </div>
      ) : (
        <>
          <Table>
            <TableHeader>
              <TableRow>
                {disputeStatus === 1 ? (
                  <TableHead className="w-10">
                    <Checkbox
                      checked={allPageSelected}
                      onCheckedChange={(checked) => togglePage(checked === true)}
                      aria-label={t("selectAllPageAria")}
                    />
                  </TableHead>
                ) : null}
                <TableHead>{t("disputes:list.studentName")}</TableHead>
                <TableHead>{t("disputes:list.assignment")}</TableHead>
                <TableHead>{t("disputes:list.question")}</TableHead>
                <TableHead>{t("disputes:list.reason")}</TableHead>
                <TableHead>{t("disputes:list.time")}</TableHead>
                <TableHead>{t("disputes:list.disputeStatus")}</TableHead>
                <TableHead>{t("disputes:list.submissionStatus")}</TableHead>
                <TableHead>{t("disputes:list.score")}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {(data?.items ?? []).map((item) => {
                const to = teacherDisputeDetailPath(item)
                const clickInState = teacherDisputeDetailState(item)
                return (
                  <TableRow
                    key={item.id}
                    className="cursor-pointer"
                    onClick={() => navigate(to, { state: clickInState })}
                  >
                    {disputeStatus === 1 ? (
                      <TableCell onClick={(event) => event.stopPropagation()}>
                        <Checkbox
                          checked={selectedIds.has(item.id)}
                          onCheckedChange={(checked) => toggleSelected(item.id, checked === true)}
                          aria-label={t("selectStudentAria", { name: item.studentName || t("unknownStudent") })}
                        />
                      </TableCell>
                    ) : null}
                    <TableCell>
                      {item.studentName || t("unknownStudent")}
                      {item.isManualOverride ? (
                        <p className="text-xs text-muted-foreground">{t("disputes:list.manualAdjusted")}</p>
                      ) : null}
                    </TableCell>
                    <TableCell className="max-w-xs truncate font-medium">
                      <Link
                        to={to}
                        state={clickInState}
                        className="text-primary underline-offset-4 hover:underline"
                        onClick={(event) => event.stopPropagation()}
                      >
                        {item.experimentTitle || t("unnamedAssignment")}
                      </Link>
                    </TableCell>
                    <TableCell className="max-w-xs truncate">
                      <Link
                        to={to}
                        state={clickInState}
                        className="text-primary underline-offset-4 hover:underline"
                        onClick={(event) => event.stopPropagation()}
                      >
                        {item.questionTitle || t("unnamedQuestion")}
                      </Link>
                    </TableCell>
                    <TableCell className="max-w-xs truncate">
                      {item.disputeReason.trim() ? item.disputeReason : "—"}
                    </TableCell>
                    <TableCell>{formatDeadlineDisplay(item.disputeTime)}</TableCell>
                    <TableCell>
                      <Badge className={DISPUTE_LIST_STATUS_BADGE_CLASS[item.disputeStatus]}>
                        {t(`disputes:listStatus.${item.disputeStatus}`)}
                      </Badge>
                    </TableCell>
                    <TableCell>
                      <Badge className={ROUND_STATUS_BADGE_CLASS[item.submissionStatus]}>
                        {t(`submissions:roundStatus.${item.submissionStatus}`)}
                      </Badge>
                    </TableCell>
                    <TableCell>
                      {item.submissionStatus === "completed" && item.score != null
                        ? String(item.score)
                        : "—"}
                    </TableCell>
                  </TableRow>
                )
              })}
            </TableBody>
          </Table>

          {data && (
            <div className="flex flex-wrap items-center justify-between gap-4">
              <p className="text-sm text-muted-foreground">
                {t("disputes:list.pageSummary", { total: data.total, page: data.page, totalPages: data.totalPages })}
              </p>
              <div className="flex flex-wrap items-center gap-1">
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  disabled={data.page <= 1}
                  onClick={() => writeListParams(data.page - 1, disputeStatus)}
                >
                  {t("prevPage")}
                </Button>
                {pageNumbers.map((pageNumber) => (
                  <Button
                    key={pageNumber}
                    type="button"
                    variant={pageNumber === data.page ? "secondary" : "outline"}
                    size="sm"
                    className={cn(pageNumber === data.page && "bg-muted")}
                    onClick={() => writeListParams(pageNumber, disputeStatus)}
                  >
                    {pageNumber}
                  </Button>
                ))}
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  disabled={data.page >= data.totalPages}
                  onClick={() => writeListParams(data.page + 1, disputeStatus)}
                >
                  {t("nextPage")}
                </Button>
              </div>
            </div>
          )}
        </>
      )}
      {disputeStatus === 1 ? (
        <ForwardDisputesDialog
          open={forwardOpen}
          onOpenChange={setForwardOpen}
          disputeIds={[...selectedIds]}
          hasManualOverride={hasManualOverride}
          onForwarded={handleForwarded}
        />
      ) : null}
    </div>
  )
}
