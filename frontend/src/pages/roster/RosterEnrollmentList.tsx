import { useCallback, useEffect, useMemo, useState } from "react"
import { useTranslation } from "react-i18next"
import { useSearchParams } from "react-router-dom"
import { KeyRound, Pencil, Search, Trash2, X } from "lucide-react"

import { Button } from "@/components/ui/button"
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
  fetchRosterEnrollments,
  ROSTER_RESET_PASSWORD,
  type PaginatedRosterEnrollments,
  type RosterEnrollment,
} from "@/lib/api/teacher-roster"
import { formatDeadlineDisplay, PAGE_SIZE } from "@/lib/mock-assignments"
import { cn } from "@/lib/utils"
import { RosterEditDialog } from "@/pages/roster/RosterEditDialog"
import { RosterResetPasswordDialog } from "@/pages/roster/RosterResetPasswordDialog"
import { RosterUnenrollDialog } from "@/pages/roster/RosterUnenrollDialog"

interface RosterEnrollmentListProps {
  reloadKey: number
  onForbidden: () => void
}

function uniqueSemesters(classes: ClassOption[]): string[] {
  const seen = new Set<string>()
  const values: string[] = []
  for (const item of classes) {
    const semester = item.semester?.trim()
    if (!semester || seen.has(semester)) {
      continue
    }
    seen.add(semester)
    values.push(semester)
  }
  return values.sort((a, b) => a.localeCompare(b, "zh"))
}

export function RosterEnrollmentList({ reloadKey, onForbidden }: RosterEnrollmentListProps) {
  const { t } = useTranslation()
  const [searchParams, setSearchParams] = useSearchParams()
  const page = Math.max(1, Number(searchParams.get("page") ?? "1") || 1)

  const [inputValue, setInputValue] = useState("")
  const [studentName, setStudentName] = useState("")
  const [classFilter, setClassFilter] = useState("all")
  const [semesterFilter, setSemesterFilter] = useState("all")
  const [classes, setClasses] = useState<ClassOption[]>([])
  const [classesError, setClassesError] = useState<string | null>(null)
  const [data, setData] = useState<PaginatedRosterEnrollments | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [actionBanner, setActionBanner] = useState<string | null>(null)
  const [actionBannerTone, setActionBannerTone] = useState<"default" | "destructive">("default")
  const [editTarget, setEditTarget] = useState<RosterEnrollment | null>(null)
  const [resetTarget, setResetTarget] = useState<RosterEnrollment | null>(null)
  const [unenrollTarget, setUnenrollTarget] = useState<RosterEnrollment | null>(null)
  const [localReloadKey, setLocalReloadKey] = useState(0)

  const classId = classFilter === "all" ? null : Number(classFilter)
  const semester = semesterFilter === "all" ? null : semesterFilter
  const semesters = useMemo(() => uniqueSemesters(classes), [classes])

  function writePage(nextPage: number) {
    setSearchParams((current) => {
      const next = new URLSearchParams(current)
      if (nextPage <= 1) {
        next.delete("page")
      } else {
        next.set("page", String(nextPage))
      }
      return next
    })
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
      setClassesError(t("roster:list.classesFailed"))
    }
  }, [])

  const loadEnrollments = useCallback(
    async (signal?: AbortSignal) => {
      setLoading(true)
      setError(null)
      try {
        const result = await fetchRosterEnrollments(
          {
            page,
            pageSize: PAGE_SIZE,
            classId: classId != null && Number.isFinite(classId) && classId > 0 ? classId : null,
            semester,
            studentName,
          },
          signal,
        )
        if (signal?.aborted) {
          return
        }
        if (result.items.length === 0 && result.total > 0 && page > 1) {
          setSearchParams((current) => {
            const next = new URLSearchParams(current)
            next.delete("page")
            return next
          })
          return
        }
        setData(result)
      } catch (err) {
        if (signal?.aborted || (err instanceof DOMException && err.name === "AbortError")) {
          return
        }
        if (err instanceof ApiError && err.status === 403) {
          onForbidden()
          return
        }
        if (err instanceof ApiError) {
          setError(err.message)
        } else {
          setError(t("roster:list.loadFailed"))
        }
        setData(null)
      } finally {
        if (!signal?.aborted) {
          setLoading(false)
        }
      }
    },
    [page, classId, semester, studentName, onForbidden, setSearchParams],
  )

  useEffect(() => {
    const controller = new AbortController()
    void loadClasses(controller.signal)
    return () => controller.abort()
  }, [loadClasses, reloadKey, localReloadKey])

  useEffect(() => {
    const controller = new AbortController()
    void loadEnrollments(controller.signal)
    return () => controller.abort()
  }, [loadEnrollments, reloadKey, localReloadKey])

  function handleSearch() {
    setStudentName(inputValue.trim())
    writePage(1)
  }

  function handleReset() {
    setInputValue("")
    setStudentName("")
    setClassFilter("all")
    setSemesterFilter("all")
    writePage(1)
  }

  function handleClassFilterChange(nextValue: string | null) {
    setClassFilter(nextValue ?? "all")
    writePage(1)
  }

  function handleSemesterFilterChange(nextValue: string | null) {
    setSemesterFilter(nextValue ?? "all")
    writePage(1)
  }

  function refetch() {
    setLocalReloadKey((key) => key + 1)
  }

  async function handleStale() {
    setActionBannerTone("destructive")
    setActionBanner(t("roster:list.stale"))
    refetch()
  }

  const isEmpty = !loading && !error && data?.total === 0
  const hasSearch = studentName.length > 0
  const hasClass = classFilter !== "all"
  const hasSemester = semesterFilter !== "all"
  const isNoData = isEmpty && !hasSearch && !hasClass && !hasSemester
  const isClassFilterEmpty = isEmpty && hasClass && !hasSemester && !hasSearch
  const isSemesterFilterEmpty = isEmpty && hasSemester && !hasClass && !hasSearch
  const isCombinedFilterEmpty = isEmpty && hasClass && hasSemester && !hasSearch
  const isSearchEmpty = isEmpty && hasSearch
  const pageNumbers = data ? Array.from({ length: data.totalPages }, (_, index) => index + 1) : []

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center gap-2">
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
            placeholder={t("roster:list.searchPlaceholder")}
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
        <Select value={semesterFilter} onValueChange={handleSemesterFilterChange}>
          <SelectTrigger className="w-[160px]">
            <SelectValue placeholder={t("allSemesters")} />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">{t("allSemesters")}</SelectItem>
            {semesters.map((item) => (
              <SelectItem key={item} value={item}>
                {item}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {loading ? (
        <div className="py-16 text-center text-muted-foreground">{t("loading")}</div>
      ) : error ? (
        <div className="flex flex-col items-center justify-center gap-4 py-16 text-center">
          <p className="text-destructive">{error}</p>
          <Button type="button" variant="outline" onClick={refetch}>
            {t("retry")}
          </Button>
        </div>
      ) : isNoData ? (
        <div className="py-16 text-center text-muted-foreground">
          {t("roster:list.empty")}
        </div>
      ) : isClassFilterEmpty ? (
        <div className="py-16 text-center text-muted-foreground">{t("roster:list.emptyClass")}</div>
      ) : isSemesterFilterEmpty ? (
        <div className="py-16 text-center text-muted-foreground">{t("roster:list.emptySemester")}</div>
      ) : isCombinedFilterEmpty || isSearchEmpty ? (
        <div className="py-16 text-center text-muted-foreground">{t("roster:list.noMatch")}</div>
      ) : (
        <>
          {classesError ? <p className="text-sm text-muted-foreground">{classesError}</p> : null}
          {actionBanner ? (
            <div
              role="alert"
              className={cn(
                "flex items-center justify-between gap-4 rounded-md border px-4 py-3 text-sm",
                actionBannerTone === "destructive"
                  ? "border-destructive/50 bg-destructive/10 text-destructive"
                  : "border-border bg-muted/40",
              )}
            >
              <p>{actionBanner}</p>
              <Button
                type="button"
                variant="ghost"
                size="icon-sm"
                aria-label={t("close")}
                className="shrink-0"
                onClick={() => setActionBanner(null)}
              >
                <X className="size-4" />
              </Button>
            </div>
          ) : null}
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t("roster:list.studentId")}</TableHead>
                <TableHead>{t("roster:list.name")}</TableHead>
                <TableHead>{t("roster:list.major")}</TableHead>
                <TableHead>{t("roster:list.class")}</TableHead>
                <TableHead>{t("roster:list.semester")}</TableHead>
                <TableHead>{t("roster:list.joinTime")}</TableHead>
                <TableHead className="w-[160px]" />
              </TableRow>
            </TableHeader>
            <TableBody>
              {(data?.items ?? []).map((item) => (
                <TableRow key={item.classStudentId}>
                  <TableCell>{item.studentId}</TableCell>
                  <TableCell>{item.studentName}</TableCell>
                  <TableCell>{item.major || "—"}</TableCell>
                  <TableCell>{item.className}</TableCell>
                  <TableCell>{item.semester || "—"}</TableCell>
                  <TableCell>{item.joinTime ? formatDeadlineDisplay(item.joinTime) : "—"}</TableCell>
                  <TableCell>
                    <div className="flex items-center gap-1">
                      <Button
                        type="button"
                        variant="ghost"
                        size="icon-sm"
                        aria-label={t("editAria")}
                        onClick={(event) => {
                          event.stopPropagation()
                          setEditTarget(item)
                        }}
                      >
                        <Pencil className="size-4" />
                      </Button>
                      <Button
                        type="button"
                        variant="ghost"
                        size="icon-sm"
                        aria-label={t("resetPasswordAria")}
                        onClick={(event) => {
                          event.stopPropagation()
                          setResetTarget(item)
                        }}
                      >
                        <KeyRound className="size-4" />
                      </Button>
                      <Button
                        type="button"
                        variant="ghost"
                        size="icon-sm"
                        aria-label={t("removeAria")}
                        onClick={(event) => {
                          event.stopPropagation()
                          setUnenrollTarget(item)
                        }}
                      >
                        <Trash2 className="size-4 text-destructive" />
                      </Button>
                    </div>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
          {data ? (
            <div className="flex flex-wrap items-center justify-between gap-4">
              <p className="text-sm text-muted-foreground">
                {t("roster:list.pageSummary", { total: data.total, page: data.page, totalPages: data.totalPages })}
              </p>
              <div className="flex flex-wrap items-center gap-1">
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  disabled={data.page <= 1}
                  onClick={() => writePage(data.page - 1)}
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
                    onClick={() => writePage(pageNumber)}
                  >
                    {pageNumber}
                  </Button>
                ))}
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  disabled={data.page >= data.totalPages}
                  onClick={() => writePage(data.page + 1)}
                >
                  {t("nextPage")}
                </Button>
              </div>
            </div>
          ) : null}
        </>
      )}

      <RosterEditDialog
        open={Boolean(editTarget)}
        enrollment={editTarget}
        classes={classes}
        onOpenChange={(open) => {
          if (!open) {
            setEditTarget(null)
          }
        }}
        onSaved={refetch}
        onStale={handleStale}
      />
      <RosterResetPasswordDialog
        open={Boolean(resetTarget)}
        enrollment={resetTarget}
        onOpenChange={(open) => {
          if (!open) {
            setResetTarget(null)
          }
        }}
        onReset={async (name) => {
          setActionBannerTone("default")
          setActionBanner(t("roster:list.resetBanner", { name, password: ROSTER_RESET_PASSWORD }))
        }}
        onStale={handleStale}
        onError={(message) => {
          setActionBannerTone("destructive")
          setActionBanner(message)
        }}
      />
      <RosterUnenrollDialog
        open={Boolean(unenrollTarget)}
        enrollment={unenrollTarget}
        onOpenChange={(open) => {
          if (!open) {
            setUnenrollTarget(null)
          }
        }}
        onRemoved={refetch}
        onStale={handleStale}
        onError={(message) => {
          setActionBannerTone("destructive")
          setActionBanner(message)
        }}
      />
    </div>
  )
}
