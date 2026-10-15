import { useCallback, useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import { Link } from "react-router-dom"

import { Button, buttonVariants } from "@/components/ui/button"
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import { ApiError } from "@/lib/api/client"
import { fetchTeacherDisputes } from "@/lib/api/teacher-disputes"
import { useAuth } from "@/lib/auth/AuthProvider"
import { cn } from "@/lib/utils"

export function Dashboard() {
  const { t } = useTranslation()
  const { user } = useAuth()
  const greetingName = user?.userName ?? user?.userAccount ?? ""
  const fallbackError = t("analytics:welcomeLoadFailed")

  const [total, setTotal] = useState<number | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [forbidden, setForbidden] = useState(false)
  const [reloadKey, setReloadKey] = useState(0)

  const loadPendingCount = useCallback(async (signal?: AbortSignal) => {
    setLoading(true)
    setError(null)
    setForbidden(false)

    try {
      const result = await fetchTeacherDisputes(
        { page: 1, pageSize: 1, disputeStatus: 1 },
        signal,
      )
      if (signal?.aborted) {
        return
      }
      setTotal(result.total)
    } catch (err) {
      if (signal?.aborted || (err instanceof DOMException && err.name === "AbortError")) {
        return
      }
      if (err instanceof ApiError && err.status === 403) {
        setForbidden(true)
        setTotal(null)
        return
      }
      if (err instanceof ApiError) {
        setError(err.message || fallbackError)
      } else {
        setError(fallbackError)
      }
      setTotal(null)
    } finally {
      if (!signal?.aborted) {
        setLoading(false)
      }
    }
  }, [fallbackError])

  useEffect(() => {
    const controller = new AbortController()
    void loadPendingCount(controller.signal)
    return () => controller.abort()
  }, [loadPendingCount, reloadKey])

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

  return (
    <div className="space-y-8">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <h1 className="text-2xl font-semibold">{t("welcomeBack", { name: greetingName })}</h1>
        <div className="flex flex-wrap gap-2">
          <Link to="/teacher/assignments/new" className={cn(buttonVariants())}>
            {t("analytics:createAssignment")}
          </Link>
          <Link to="/teacher/questions/new" className={cn(buttonVariants({ variant: "outline" }))}>
            {t("analytics:createQuestion")}
          </Link>
          <Link to="/teacher/roster" className={cn(buttonVariants({ variant: "outline" }))}>
            {t("analytics:viewRoster")}
          </Link>
        </div>
      </div>

      <Link to="/teacher/disputes" className="block max-w-xl transition-opacity hover:opacity-90">
        <Card className="cursor-pointer hover:bg-muted/30">
          <CardHeader>
            <CardTitle>{t("analytics:pendingTitle")}</CardTitle>
            <CardDescription>{t("analytics:pendingDescription")}</CardDescription>
          </CardHeader>
          <CardContent>
            {loading ? (
              <p className="text-muted-foreground">{t("loading")}</p>
            ) : error ? (
              <div className="flex flex-col items-start gap-3">
                <p className="text-destructive">{error}</p>
                <Button
                  type="button"
                  variant="outline"
                  onClick={(event) => {
                    event.preventDefault()
                    event.stopPropagation()
                    setReloadKey((key) => key + 1)
                  }}
                >
                  {t("retry")}
                </Button>
              </div>
            ) : (
              <p className="text-3xl font-semibold">{total ?? 0}</p>
            )}
          </CardContent>
        </Card>
      </Link>
    </div>
  )
}
