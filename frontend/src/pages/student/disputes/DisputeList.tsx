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
  DISPUTE_LIST_STATUS_BADGE_CLASS,
  fetchStudentDisputes,
  studentDisputeDetailPath,
  type PaginatedStudentDisputes,
} from "@/lib/api/student-disputes"
import { formatDeadlineDisplay, PAGE_SIZE } from "@/lib/mock-assignments"
import { cn } from "@/lib/utils"

import { WithdrawDisputeDialog } from "@/pages/student/disputes/WithdrawDisputeDialog"

export function DisputeList() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const page = Math.max(1, Number(searchParams.get("page") ?? "1") || 1)

  const [data, setData] = useState<PaginatedStudentDisputes | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [forbidden, setForbidden] = useState(false)
  const [reloadKey, setReloadKey] = useState(0)
  const [withdrawId, setWithdrawId] = useState<number | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)

  const loadDisputes = useCallback(
    async (signal?: AbortSignal) => {
      setLoading(true)
      setError(null)
      setForbidden(false)

      try {
        const result = await fetchStudentDisputes({ page, pageSize: PAGE_SIZE }, signal)
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
          setError(t("disputes:list.loadFailed"))
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
    void loadDisputes(controller.signal)
    return () => controller.abort()
  }, [loadDisputes, reloadKey])

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
      {actionError ? <p className="text-sm text-destructive">{actionError}</p> : null}
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
        <div className="py-16 text-center text-muted-foreground">{t("disputes:list.emptyStudent")}</div>
      ) : (
        <>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t("disputes:list.experimentTitle")}</TableHead>
                <TableHead>{t("disputes:list.question")}</TableHead>
                <TableHead>{t("disputes:list.time")}</TableHead>
                <TableHead>{t("disputes:list.disputeStatus")}</TableHead>
                <TableHead>{t("disputes:list.score")}</TableHead>
                <TableHead>{t("disputes:list.actions")}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {(data?.items ?? []).map((item) => {
                const to = studentDisputeDetailPath(item)
                return (
                  <TableRow
                    key={item.id}
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
                    <TableCell className="max-w-xs truncate">
                      <Link
                        to={to}
                        className="text-primary underline-offset-4 hover:underline"
                        onClick={(event) => event.stopPropagation()}
                      >
                        {item.questionTitle || t("unnamedQuestion")}
                      </Link>
                    </TableCell>
                    <TableCell>{formatDeadlineDisplay(item.disputeTime)}</TableCell>
                    <TableCell>
                      <Badge className={DISPUTE_LIST_STATUS_BADGE_CLASS[item.disputeStatus]}>
                        {t(`disputes:listStatus.${item.disputeStatus}`)}
                      </Badge>
                    </TableCell>
                    <TableCell>{item.score == null ? "—" : String(item.score)}</TableCell>
                    <TableCell>
                      {item.disputeStatus === 1 ? (
                        <Button
                          type="button"
                          variant="outline"
                          size="sm"
                          onClick={(event) => {
                            event.stopPropagation()
                            setActionError(null)
                            setWithdrawId(item.id)
                          }}
                        >
                          {t("disputes:list.withdraw")}
                        </Button>
                      ) : (
                        "—"
                      )}
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
      <WithdrawDisputeDialog
        open={withdrawId != null}
        onOpenChange={(open) => {
          if (!open) setWithdrawId(null)
        }}
        disputeId={withdrawId}
        onWithdrawn={() => {
          setActionError(null)
          setWithdrawId(null)
          setReloadKey((k) => k + 1)
        }}
        onError={(message) => {
          setActionError(message)
          setWithdrawId(null)
          setReloadKey((k) => k + 1)
        }}
      />
    </div>
  )
}
