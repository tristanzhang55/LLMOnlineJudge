import { useCallback, useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import { Link, useNavigate, useSearchParams } from "react-router-dom"
import { Search } from "lucide-react"

import { Badge } from "@/components/ui/badge"
import { Button, buttonVariants } from "@/components/ui/button"
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
  fetchTeacherSubmissions,
  ROUND_STATUS_BADGE_CLASS,
  type PaginatedTeacherSubmissions,
} from "@/lib/api/teacher-submissions"
import { formatDeadlineDisplay, PAGE_SIZE } from "@/lib/mock-assignments"
import { cn } from "@/lib/utils"

function formatScore(status: string, score: number | null): string {
  if (status !== "completed" || score == null) {
    return "—"
  }
  return String(score)
}

function detailPath(experimentId: number, studentId: number): string {
  return `/teacher/submissions/${experimentId}/students/${studentId}`
}

export function SubmissionList() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const page = Math.max(1, Number(searchParams.get("page") ?? "1") || 1)

  const [inputValue, setInputValue] = useState("")
  const [studentName, setStudentName] = useState("")
  const [classFilter, setClassFilter] = useState("all")
  const [data, setData] = useState<PaginatedTeacherSubmissions | null>(null)
  const [classes, setClasses] = useState<ClassOption[]>([])
  const [classesError, setClassesError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [forbidden, setForbidden] = useState(false)
  const [reloadKey, setReloadKey] = useState(0)

  const classId = classFilter === "all" ? null : Number(classFilter)
  const hasFilters = Boolean(studentName.trim()) || classId != null

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
      setClassesError(t("submissions:list.classesFailed"))
    }
  }, [])

  const loadSubmissions = useCallback(
    async (signal?: AbortSignal) => {
      setLoading(true)
      setError(null)
      setForbidden(false)

      try {
        const result = await fetchTeacherSubmissions(
          {
            page,
            pageSize: PAGE_SIZE,
            studentName,
            classId: classId != null && Number.isFinite(classId) && classId > 0 ? classId : null,
          },
          signal,
        )
        if (signal?.aborted) {
          return
        }

        if (result.items.length === 0 && result.total > 0 && page > 1) {
          setSearchParams({ page: "1" })
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
          setError(t("submissions:list.loadFailed"))
        }
        setData(null)
      } finally {
        if (!signal?.aborted) {
          setLoading(false)
        }
      }
    },
    [page, studentName, classId, setSearchParams],
  )

  useEffect(() => {
    const controller = new AbortController()
    void loadClasses(controller.signal)
    return () => controller.abort()
  }, [loadClasses, reloadKey])

  useEffect(() => {
    const controller = new AbortController()
    void loadSubmissions(controller.signal)
    return () => controller.abort()
  }, [loadSubmissions, reloadKey])

  function handleSearch() {
    setStudentName(inputValue.trim())
    setSearchParams({ page: "1" })
  }

  function handleReset() {
    setInputValue("")
    setStudentName("")
    setClassFilter("all")
    navigate("/teacher/submissions", { replace: true })
  }

  function handleClassFilterChange(nextValue: string | null) {
    setClassFilter(nextValue ?? "all")
    setSearchParams({ page: "1" })
  }

  function goToPage(nextPage: number) {
    setSearchParams({ page: String(nextPage) })
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
            placeholder={t("submissions:list.searchPlaceholder")}
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
          {hasFilters ? t("submissions:list.emptyFiltered") : t("submissions:list.empty")}
        </div>
      ) : (
        <>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t("submissions:list.studentName")}</TableHead>
                <TableHead>{t("submissions:list.experimentTitle")}</TableHead>
                <TableHead>{t("submissions:list.submittedAt")}</TableHead>
                <TableHead>{t("submissions:list.questionCount")}</TableHead>
                <TableHead>{t("submissions:list.status")}</TableHead>
                <TableHead>{t("submissions:list.score")}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {(data?.items ?? []).map((item) => {
                const to = detailPath(item.experimentId, item.studentId)
                return (
                  <TableRow
                    key={`${item.studentId}-${item.experimentId}-${item.submitTime}`}
                    className="cursor-pointer"
                    onClick={() => navigate(to)}
                  >
                    <TableCell>{item.studentName || t("unknownStudent")}</TableCell>
                    <TableCell className="max-w-xs truncate font-medium">
                      <Link
                        to={to}
                        className="text-primary underline-offset-4 hover:underline"
                        onClick={(event) => event.stopPropagation()}
                      >
                        {item.experimentTitle || t("unnamedAssignment")}
                      </Link>
                    </TableCell>
                    <TableCell>{formatDeadlineDisplay(item.submitTime)}</TableCell>
                    <TableCell>{item.questionCount}</TableCell>
                    <TableCell>
                      <Badge className={ROUND_STATUS_BADGE_CLASS[item.status]}>
                        {t(`submissions:roundStatus.${item.status}`)}
                      </Badge>
                    </TableCell>
                    <TableCell>{formatScore(item.status, item.score)}</TableCell>
                  </TableRow>
                )
              })}
            </TableBody>
          </Table>

          {data && (
            <div className="flex flex-wrap items-center justify-between gap-4">
              <p className="text-sm text-muted-foreground">
                {t("submissions:list.pageSummary", { total: data.total, page: data.page, totalPages: data.totalPages })}
              </p>
              <div className="flex flex-wrap items-center gap-1">
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  disabled={data.page <= 1}
                  onClick={() => goToPage(data.page - 1)}
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
                    onClick={() => goToPage(pageNumber)}
                  >
                    {pageNumber}
                  </Button>
                ))}
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  disabled={data.page >= data.totalPages}
                  onClick={() => goToPage(data.page + 1)}
                >
                  {t("nextPage")}
                </Button>
              </div>
            </div>
          )}
        </>
      )}
    </div>
  )
}
