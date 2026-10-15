import { useState, type FormEvent } from "react"
import { Eye, EyeOff } from "lucide-react"
import { useTranslation } from "react-i18next"
import { useNavigate } from "react-router-dom"

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
import { ApiError } from "@/lib/api/client"
import { useAuth } from "@/lib/auth/AuthProvider"
import {
  changePassword,
  NEW_PASSWORD_MAX,
  validatePasswordChange,
  type PasswordErrorCode,
  type PasswordFieldErrors,
} from "@/lib/api/user"

interface PasswordChangeFormProps {
  onForbidden: () => void
}

function mapPasswordCodes(
  codes: Partial<Record<"oldPassword" | "newPassword" | "confirmPassword", PasswordErrorCode>>,
  t: (key: `settings:password.errors.${PasswordErrorCode}`) => string,
): PasswordFieldErrors {
  const mapped: PasswordFieldErrors = {}
  if (codes.oldPassword) {
    mapped.oldPassword = t(`settings:password.errors.${codes.oldPassword}`)
  }
  if (codes.newPassword) {
    mapped.newPassword = t(`settings:password.errors.${codes.newPassword}`)
  }
  if (codes.confirmPassword) {
    mapped.confirmPassword = t(`settings:password.errors.${codes.confirmPassword}`)
  }
  return mapped
}

export function PasswordChangeForm({ onForbidden }: PasswordChangeFormProps) {
  const { t } = useTranslation()
  const { logout } = useAuth()
  const navigate = useNavigate()
  const [oldPassword, setOldPassword] = useState("")
  const [newPassword, setNewPassword] = useState("")
  const [confirmPassword, setConfirmPassword] = useState("")
  const [showPasswords, setShowPasswords] = useState(false)
  const [fieldErrors, setFieldErrors] = useState<PasswordFieldErrors>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const fallbackError = t("settings:password.fallback")
  const inputType = showPasswords ? "text" : "password"
  const toggleLabel = showPasswords ? t("settings:password.hide") : t("settings:password.show")

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    const nextErrors = validatePasswordChange({
      oldPassword,
      newPassword,
      confirmPassword,
    })
    if (Object.keys(nextErrors).length > 0) {
      setFieldErrors(mapPasswordCodes(nextErrors, t))
      return
    }

    setSubmitting(true)
    setFieldErrors({})
    setFormError(null)

    try {
      await changePassword(oldPassword, newPassword)
      await logout()
      navigate("/login", { replace: true })
    } catch (err) {
      if (err instanceof ApiError && err.status === 403) {
        onForbidden()
        return
      }
      if (err instanceof ApiError && err.status === 400) {
        const mapped: PasswordFieldErrors = {}
        for (const item of err.fieldErrors ?? []) {
          if (item.field === "oldPassword") {
            mapped.oldPassword = item.message
          } else if (item.field === "newPassword") {
            mapped.newPassword = item.message
          }
        }
        setFieldErrors(mapped)
        if (Object.keys(mapped).length === 0) {
          setFormError(err.message || fallbackError)
        }
        return
      }
      if (err instanceof ApiError) {
        setFormError(err.message || fallbackError)
        return
      }
      setFormError(fallbackError)
    } finally {
      setSubmitting(false)
    }
  }

  function renderToggle() {
    return (
      <Button
        type="button"
        variant="ghost"
        size="icon-sm"
        className="absolute top-1/2 right-1 -translate-y-1/2"
        aria-label={toggleLabel}
        disabled={submitting}
        onClick={() => setShowPasswords((value) => !value)}
      >
        {showPasswords ? <EyeOff /> : <Eye />}
      </Button>
    )
  }

  return (
    <Card className="max-w-xl">
      <CardHeader>
        <CardTitle>{t("settings:password.title")}</CardTitle>
        <CardDescription>{t("settings:password.descriptionTeacher")}</CardDescription>
      </CardHeader>
      <CardContent>
        <form className="space-y-4" onSubmit={(event) => void handleSubmit(event)}>
          <div className="space-y-2">
            <Label htmlFor="oldPassword">{t("settings:password.current")}</Label>
            <div className="relative">
              <Input
                id="oldPassword"
                name="oldPassword"
                autoComplete="current-password"
                type={inputType}
                value={oldPassword}
                disabled={submitting}
                aria-invalid={Boolean(fieldErrors.oldPassword)}
                className="pr-9"
                onChange={(event) => setOldPassword(event.target.value)}
              />
              {renderToggle()}
            </div>
            {fieldErrors.oldPassword ? (
              <p className="text-sm text-destructive">{fieldErrors.oldPassword}</p>
            ) : null}
          </div>

          <div className="space-y-2">
            <Label htmlFor="newPassword">{t("settings:password.new")}</Label>
            <div className="relative">
              <Input
                id="newPassword"
                name="newPassword"
                autoComplete="new-password"
                type={inputType}
                value={newPassword}
                maxLength={NEW_PASSWORD_MAX}
                disabled={submitting}
                aria-invalid={Boolean(fieldErrors.newPassword)}
                className="pr-9"
                onChange={(event) => setNewPassword(event.target.value)}
              />
              {renderToggle()}
            </div>
            {fieldErrors.newPassword ? (
              <p className="text-sm text-destructive">{fieldErrors.newPassword}</p>
            ) : null}
            <p className="text-sm text-muted-foreground">
              {t("settings:password.hint")}
            </p>
          </div>

          <div className="space-y-2">
            <Label htmlFor="confirmPassword">{t("settings:password.confirm")}</Label>
            <div className="relative">
              <Input
                id="confirmPassword"
                name="confirmPassword"
                autoComplete="new-password"
                type={inputType}
                value={confirmPassword}
                maxLength={NEW_PASSWORD_MAX}
                disabled={submitting}
                aria-invalid={Boolean(fieldErrors.confirmPassword)}
                className="pr-9"
                onChange={(event) => setConfirmPassword(event.target.value)}
              />
              {renderToggle()}
            </div>
            {fieldErrors.confirmPassword ? (
              <p className="text-sm text-destructive">{fieldErrors.confirmPassword}</p>
            ) : null}
          </div>

          {formError ? <p className="text-sm text-destructive">{formError}</p> : null}

          <Button type="submit" disabled={submitting}>
            {submitting ? t("saving") : t("settings:password.submit")}
          </Button>
        </form>
      </CardContent>
    </Card>
  )
}
