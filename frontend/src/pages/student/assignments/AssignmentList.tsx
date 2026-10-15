import { useCallback, useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import { Link, useSearchParams } from "react-router-dom"

import { Badge } from "@/components/ui/badge"
import { Button, buttonVariants } from "@/components/ui/button"
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
  fetchStudentExperiments,
  type PaginatedStudentExperimentList,
  type StudentExperimentListItem,
} from "@/lib/api/student-experiments"
import { formatDeadlineDisplay, isDueSoon, PAGE_SIZE } from "@/lib/mock-assignments"
import { cn } from "@/lib/utils"

const STUDENT_STATUS_BADGE_CLASS = {
  1: "border-transparent bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400",
  2: "border-transparent bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-400",
} as const

function AnswerStatusButton({ item }: { item: StudentExperimentListItem }) {
  const { t } = useTranslation()
  const to = `/student/assignments/${item.id}`

  if (item.status === 2) {
    return (
      <Link to={to} className={cn(buttonVariants({ variant: "outline", size: "sm" }), "text-muted-foreground")}>
        {t("student:list.closed")}
      </Link>
    )
  }
  if (item.locked) {
    return (
      <Link
        to={to}
        className={cn(
          buttonVariants({ size: "sm" }),
          "border-transparent bg-green-100 text-green-800 hover:bg-green-100/80 dark:bg-green-900/30 dark:text-green-400",
        )}
      >
        {t("student:list.submitted")}
      </Link>
    )
  }
  if (item.resubmissionEligible) {
    return (
      <Link
        to={to}
        className={cn(
          buttonVariants({ size: "sm" }),
          "border-transparent bg-amber-100 text-amber-900 hover:bg-amber-100/80 dark:bg-amber-900/30 dark:text-amber-400",
        )}
      >
        {t("student:list.resubmit")}
      </Link>
    )
  }
  return (
    <Link to={to} className={cn(buttonVariants({ size: "sm" }))}>
      {t("student:list.pending")}
    </Link>
  )
}

export function AssignmentList() {
  const { t } = useTranslation()
  const [searchParams, setSearchParams] = useSearchParams()
  const page = Math.max(1, Number(searchParams.get("page") ?? "1") || 1)

  const [data, setData] = useState<PaginatedStudentExperimentList | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [forbidden, setForbidden] = useState(false)
  const [reloadKey, setReloadKey] = useState(0)

  const loadExperiments = useCallback(
    async (signal?: AbortSignal) => {
      setLoading(true)
      setError(null)
      setForbidden(false)

      try {
        const result = await fetchStudentExperiments(
          { page, pageSize: PAGE_SIZE },
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
          setError(t("student:list.loadFailed"))
        }
        setData(null)
      } finally {
        if (!signal?.aborted) {
          setLoading(false)
        }
      }
    },
    [page, setSearchParams],
  )

  useEffect(() => {
    const controller = new AbortController()
    void loadExperiments(controller.signal)
    return () => controller.abort()
  }, [loadExperiments, reloadKey])

  function goToPage(nextPage: number) {
    setSearchParams({ page: String(nextPage) })
  }

  if (forbidden) {
    return (
      <div className="flex flex-col items-center justify-center gap-4 py-16 text-center">
        <p className="text-muted-foreground">{t("forbidden")}</p>
        <Link to="/student" className={cn(buttonVariants({ variant: "outline" }))}>
          {t("backHome")}
        </Link>
      </div>
    )
  }

  const pageNumbers = data ? Array.from({ length: data.totalPages }, (_, index) => index + 1) : []
  const isEmpty = !loading && !error && data?.total === 0

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-semibold">{t("student:list.heading")}</h1>

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
        <div className="py-16 text-center text-muted-foreground">{t("student:list.empty")}</div>
      ) : (
        <>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t("student:list.title")}</TableHead>
                <TableHead>{t("student:list.status")}</TableHead>
                <TableHead>{t("student:list.answering")}</TableHead>
                <TableHead>{t("student:list.deadline")}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {(data?.items ?? []).map((item) => (
                <TableRow key={item.id}>
                  <TableCell className="max-w-xs truncate font-medium">
                    <Link
                      to={`/student/assignments/${item.id}`}
                      className="text-primary underline-offset-4 hover:underline"
                    >
                      {item.title || t("unnamedAssignment")}
                    </Link>
                  </TableCell>
                  <TableCell>
                    <Badge className={STUDENT_STATUS_BADGE_CLASS[item.status]}>
                      {t(`assignments:status.${item.status}`)}
                    </Badge>
                  </TableCell>
                  <TableCell>
                    <AnswerStatusButton item={item} />
                  </TableCell>
                  <TableCell
                    className={cn(
                      item.deadlineTime != null &&
                        item.status === 1 &&
                        isDueSoon(item.deadlineTime, item.status) &&
                        "font-medium text-amber-600 dark:text-amber-400",
                    )}
                  >
                    {item.deadlineTime == null
                      ? "—"
                      : formatDeadlineDisplay(item.deadlineTime)}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>

          {data && (
            <div className="flex flex-wrap items-center justify-between gap-4">
              <p className="text-sm text-muted-foreground">
                {t("student:list.pageSummary", { total: data.total, page: data.page, totalPages: data.totalPages })}
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
