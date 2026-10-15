import { useCallback, useEffect, useState } from "react"
import { useTranslation } from "react-i18next"

import { Button } from "@/components/ui/button"
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import { ApiError } from "@/lib/api/client"
import { fetchUserProfile, type UserProfile } from "@/lib/api/user"

interface ProfileCardProps {
  onForbidden: () => void
}

function displayValue(value: string | null | undefined): string {
  if (value == null || value === "") {
    return "—"
  }
  return value
}

export function ProfileCard({ onForbidden }: ProfileCardProps) {
  const { t } = useTranslation()
  const [profile, setProfile] = useState<UserProfile | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [reloadKey, setReloadKey] = useState(0)
  const fallbackError = t("settings:profile.fallback")

  const loadProfile = useCallback(
    async (signal?: AbortSignal) => {
      setLoading(true)
      setError(null)

      try {
        const result = await fetchUserProfile(signal)
        if (signal?.aborted) {
          return
        }
        setProfile(result)
      } catch (err) {
        if (signal?.aborted || (err instanceof DOMException && err.name === "AbortError")) {
          return
        }
        if (err instanceof ApiError && err.status === 403) {
          onForbidden()
          setProfile(null)
          return
        }
        if (err instanceof ApiError) {
          setError(err.message || fallbackError)
        } else {
          setError(fallbackError)
        }
        setProfile(null)
      } finally {
        if (!signal?.aborted) {
          setLoading(false)
        }
      }
    },
    [onForbidden, fallbackError],
  )

  useEffect(() => {
    const controller = new AbortController()
    void loadProfile(controller.signal)
    return () => controller.abort()
  }, [loadProfile, reloadKey])

  return (
    <Card className="max-w-xl">
      <CardHeader>
        <CardTitle>{t("settings:profile.title")}</CardTitle>
      </CardHeader>
      <CardContent>
        {loading ? (
          <p className="text-muted-foreground">{t("loading")}</p>
        ) : error ? (
          <div className="flex flex-col items-start gap-3">
            <p className="text-destructive">{error}</p>
            <Button type="button" variant="outline" onClick={() => setReloadKey((key) => key + 1)}>
              {t("retry")}
            </Button>
          </div>
        ) : profile ? (
          <div className="space-y-3">
            <div className="flex justify-between gap-4">
              <span className="text-muted-foreground">{t("settings:profile.account")}</span>
              <span className="font-medium">{displayValue(profile.userAccount)}</span>
            </div>
            <div className="flex justify-between gap-4">
              <span className="text-muted-foreground">{t("settings:profile.name")}</span>
              <span className="font-medium">{displayValue(profile.userName)}</span>
            </div>
          </div>
        ) : null}
      </CardContent>
    </Card>
  )
}
