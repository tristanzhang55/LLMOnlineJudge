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
import {
  fetchStudentDashboardSummary,
  type StudentDashboardSummary,
} from "@/lib/api/student-dashboard"
import { useAuth } from "@/lib/auth/AuthProvider"
import { cn } from "@/lib/utils"

const EMPTY_SUMMARY: StudentDashboardSummary = {
  pendingAssignments: 0,
  disputesWithNewReply: 0,
}

export function StudentDashboard() {
  const { t } = useTranslation()
  const { user } = useAuth()
  const greetingName = user?.userName ?? user?.userAccount ?? ""
  const fallbackError = t("student:dashboard.loadFailed")

  const [summary, setSummary] = useState<StudentDashboardSummary | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [forbidden, setForbidden] = useState(false)
  const [reloadKey, setReloadKey] = useState(0)

  const loadSummary = useCallback(async (signal?: AbortSignal) => {
    setLoading(true)
    setError(null)
    setForbidden(false)

    try {
      const result = await fetchStudentDashboardSummary(signal)
      if (signal?.aborted) {
        return
      }
      setSummary(result)
    } catch (err) {
      if (signal?.aborted || (err instanceof DOMException && err.name === "AbortError")) {
        return
      }
      if (err instanceof ApiError && err.status === 403) {
        setForbidden(true)
        setSummary(null)
        return
      }
      if (err instanceof ApiError) {
        setError(err.message || fallbackError)
      } else {
        setError(fallbackError)
      }
      setSummary(null)
    } finally {
      if (!signal?.aborted) {
        setLoading(false)
      }
    }
  }, [fallbackError])

  useEffect(() => {
    const controller = new AbortController()
    void loadSummary(controller.signal)
    return () => controller.abort()
  }, [loadSummary, reloadKey])

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

  const counts = summary ?? EMPTY_SUMMARY

  return (
    <div className="space-y-8">
      <h1 className="text-2xl font-semibold">{t("welcomeBack", { name: greetingName })}</h1>

      {error ? (
        <div className="flex flex-col items-start gap-3">
          <p className="text-destructive">{error}</p>
          <Button type="button" variant="outline" onClick={() => setReloadKey((key) => key + 1)}>
            {t("retry")}
          </Button>
        </div>
      ) : null}

      <div className="grid gap-4 md:grid-cols-2">
        <Link to="/student/assignments" className="block transition-opacity hover:opacity-90">
          <Card className="cursor-pointer hover:bg-muted/30">
            <CardHeader>
              <CardTitle>{t("student:dashboard.pendingTitle")}</CardTitle>
              <CardDescription>{t("student:dashboard.pendingDescription")}</CardDescription>
            </CardHeader>
            <CardContent>
              {loading ? (
                <p className="text-muted-foreground">{t("loading")}</p>
              ) : error ? null : (
                <p className="text-3xl font-semibold">{counts.pendingAssignments}</p>
              )}
            </CardContent>
          </Card>
        </Link>

        <Link to="/student/disputes" className="block transition-opacity hover:opacity-90">
          <Card className="cursor-pointer hover:bg-muted/30">
            <CardHeader>
              <CardTitle>{t("student:dashboard.repliesTitle")}</CardTitle>
              <CardDescription>{t("student:dashboard.repliesDescription")}</CardDescription>
            </CardHeader>
            <CardContent>
              {loading ? (
                <p className="text-muted-foreground">{t("loading")}</p>
              ) : error ? null : (
                <p className="text-3xl font-semibold">{counts.disputesWithNewReply}</p>
              )}
            </CardContent>
          </Card>
        </Link>
      </div>
    </div>
  )
}
