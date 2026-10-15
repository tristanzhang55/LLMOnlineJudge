import { useState, type FormEvent } from "react"
import { useTranslation } from "react-i18next"
import { Navigate, useNavigate, useSearchParams } from "react-router-dom"

import { LanguageSwitcher } from "@/components/LanguageSwitcher"
import { Button } from "@/components/ui/button"
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { useDocumentTitle } from "@/hooks/useDocumentTitle"
import { ApiError } from "@/lib/api/client"
import { useAuth } from "@/lib/auth/AuthProvider"
import { resolvePostLoginPath } from "@/lib/auth/redirect"

export function Login() {
  const { t } = useTranslation("auth")
  const { user, login, logout } = useAuth()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const redirectParam = searchParams.get("redirect")

  const [userAccount, setUserAccount] = useState("")
  const [userPassword, setUserPassword] = useState("")
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [noPortal, setNoPortal] = useState(false)

  useDocumentTitle(t("signIn"))

  const hasPortal =
    user?.userType === "teacher" || user?.userType === "student"

  const handleLogout = () => {
    void logout().then(() => {
      setNoPortal(false)
      setError(null)
      navigate("/login", { replace: true })
    })
  }

  if (user && hasPortal && !noPortal) {
    const destination = resolvePostLoginPath(user.userType, redirectParam)
    if (destination) {
      return <Navigate to={destination} replace />
    }
  }

  if (user && (!hasPortal || noPortal)) {
    return (
      <div className="relative flex min-h-screen items-center justify-center p-8">
        <div className="absolute top-8 right-8">
          <LanguageSwitcher />
        </div>
        <Card className="w-full max-w-md">
          <CardHeader>
            <CardTitle>{t("signIn")}</CardTitle>
            <CardDescription>{t("noPortal")}</CardDescription>
          </CardHeader>
          <CardContent>
            <Button type="button" variant="outline" onClick={handleLogout}>
              {t("signOut")}
            </Button>
          </CardContent>
        </Card>
      </div>
    )
  }

  const handleLoginSuccess = (loggedInUser: NonNullable<typeof user>) => {
    const destination = resolvePostLoginPath(loggedInUser.userType, redirectParam)
    if (destination) {
      navigate(destination, { replace: true })
      return
    }
    setNoPortal(true)
  }

  const submitLogin = async (account: string, password: string) => {
    if (loading) {
      return
    }
    setError(null)
    setNoPortal(false)
    setLoading(true)
    try {
      const loggedInUser = await login(account, password)
      handleLoginSuccess(loggedInUser)
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.message)
      } else {
        setError(t("loginFailed"))
      }
    } finally {
      setLoading(false)
    }
  }

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!userAccount.trim() || !userPassword) {
      setError(t("emptyCredentials"))
      return
    }
    void submitLogin(userAccount.trim(), userPassword)
  }

  return (
    <div className="relative flex min-h-screen items-center justify-center p-8">
      <div className="absolute top-8 right-8">
        <LanguageSwitcher />
      </div>
      <Card className="w-full max-w-md">
        <CardHeader className="items-center text-center">
          <CardTitle>{t("signIn")}</CardTitle>
          <CardDescription>{t("subtitle")}</CardDescription>
        </CardHeader>
        <CardContent className="space-y-6">
          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="userAccount">{t("account")}</Label>
              <Input
                id="userAccount"
                name="userAccount"
                autoComplete="username"
                value={userAccount}
                onChange={(event) => setUserAccount(event.target.value)}
                disabled={loading}
                required
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="userPassword">{t("password")}</Label>
              <Input
                id="userPassword"
                name="userPassword"
                type="password"
                autoComplete="current-password"
                value={userPassword}
                onChange={(event) => setUserPassword(event.target.value)}
                disabled={loading}
                required
              />
            </div>
            {error && (
              <p className="text-sm text-destructive" role="alert">
                {error}
              </p>
            )}
            <Button type="submit" className="w-full" disabled={loading}>
              {loading ? t("signingIn") : t("signIn")}
            </Button>
            <p className="text-sm text-muted-foreground items-center text-center">{t("forgot")}</p>
          </form>
        </CardContent>
      </Card>
    </div>
  )
}
