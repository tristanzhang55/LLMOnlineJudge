import { useCallback, useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import { Link, useNavigate, useSearchParams } from "react-router-dom"

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
  fetchSubmissionHistory,
  type PaginatedStudentSubmissionHistory,
  type SubmissionRoundStatus,
} from "@/lib/api/student-submissions"
import { formatDeadlineDisplay, PAGE_SIZE } from "@/lib/mock-assignments"
import { cn } from "@/lib/utils"

const ROUND_STATUS_BADGE_CLASS: Record<SubmissionRoundStatus, string> = {
  pending:
    "border-border bg-background text-muted-foreground dark:border-input dark:bg-input/30",
  grading_in_progress:
    "border-transparent bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-400",
  completed:
    "border-transparent bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400",
  grading_failed:
    "border-transparent bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-400",
}

function formatScore(status: SubmissionRoundStatus, score: number | null): string {
  if (status !== "completed" || score == null) {
    return "—"
  }
  return String(score)
}

export function SubmissionHistory() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const page = Math.max(1, Number(searchParams.get("page") ?? "1") || 1)

  const [data, setData] = useState<PaginatedStudentSubmissionHistory | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [forbidden, setForbidden] = useState(false)
  const [reloadKey, setReloadKey] = useState(0)

  const loadHistory = useCallback(
    async (signal?: AbortSignal) => {
      setLoading(true)
      setError(null)
      setForbidden(false)

      try {
        const result = await fetchSubmissionHistory({ page, pageSize: PAGE_SIZE }, signal)
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
          setError(t("student:history.loadFailed"))
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
    void loadHistory(controller.signal)
    return () => controller.abort()
  }, [loadHistory, reloadKey])

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
      <h1 className="text-2xl font-semibold">{t("student:history.heading")}</h1>

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
        <div className="py-16 text-center text-muted-foreground">{t("student:history.empty")}</div>
      ) : (
        <>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t("student:history.experimentTitle")}</TableHead>
                <TableHead>{t("student:history.submittedAt")}</TableHead>
                <TableHead>{t("student:history.questionCount")}</TableHead>
                <TableHead>{t("student:history.status")}</TableHead>
                <TableHead>{t("student:history.score")}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {(data?.items ?? []).map((item) => {
                const to = `/student/assignments/${item.experimentId}`
                return (
                  <TableRow
                    key={`${item.experimentId}-${item.submitTime}`}
                    className="cursor-pointer"
                    onClick={() => navigate(to)}
                  >
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
                {t("student:history.pageSummary", { total: data.total, page: data.page, totalPages: data.totalPages })}
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
