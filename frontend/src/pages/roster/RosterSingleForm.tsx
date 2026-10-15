import { useState, type FormEvent } from "react"
import { useTranslation } from "react-i18next"

import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { ApiError } from "@/lib/api/client"
import {
  createTeacherStudent,
  ROSTER_FIELD_MAX,
  type RosterPersistResult,
} from "@/lib/api/teacher-roster"
import { cn } from "@/lib/utils"

interface RosterSingleFormProps {
  onForbidden: () => void
  onCancel: () => void
  onRosterChanged?: () => void
}

type FormFields = {
  studentId: string
  studentName: string
  major: string
  className: string
  semester: string
  initialPassword: string
}

const EMPTY_FORM: FormFields = {
  studentId: "",
  studentName: "",
  major: "",
  className: "",
  semester: "",
  initialPassword: "",
}

const REQUIRED_FIELDS: (keyof Omit<FormFields, "initialPassword">)[] = [
  "studentId",
  "studentName",
  "major",
  "className",
  "semester",
]

const SUCCESS_RESULTS = new Set<RosterPersistResult>(["created", "enrolled", "already_imported"])

export function RosterSingleForm({ onForbidden, onCancel, onRosterChanged }: RosterSingleFormProps) {
  const { t } = useTranslation()
  const [values, setValues] = useState<FormFields>(EMPTY_FORM)
  const [fieldErrors, setFieldErrors] = useState<Partial<Record<keyof FormFields, string>>>({})
  const [banner, setBanner] = useState<string | null>(null)
  const [bannerTone, setBannerTone] = useState<"default" | "destructive">("default")
  const [submitting, setSubmitting] = useState(false)

  function updateField<K extends keyof FormFields>(key: K, value: FormFields[K]) {
    setValues((current) => ({ ...current, [key]: value }))
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const nextErrors: Partial<Record<keyof FormFields, string>> = {}
    for (const key of REQUIRED_FIELDS) {
      if (!values[key].trim()) {
        nextErrors[key] = t("roster:single.required")
      }
    }
    setFieldErrors(nextErrors)
    if (Object.keys(nextErrors).length > 0) {
      return
    }

    setSubmitting(true)
    setBanner(null)
    try {
      const result = await createTeacherStudent({
        studentId: values.studentId,
        studentName: values.studentName,
        major: values.major,
        className: values.className,
        semester: values.semester,
        initialPassword: /\S/.test(values.initialPassword) ? values.initialPassword : null,
      })
      if (SUCCESS_RESULTS.has(result.result)) {
        const extra = result.messages.length > 0 ? `.${result.messages.join(t("listSep"))}` : ""
        setBannerTone("default")
        setBanner(`${t(`roster:import.persistResult.${result.result}`)}${extra}`)
        setValues(EMPTY_FORM)
        setFieldErrors({})
        onRosterChanged?.()
      } else {
        setBannerTone("destructive")
        setBanner(result.messages.join(t("listSep")) || t("roster:single.addFailed"))
      }
    } catch (err) {
      if (err instanceof ApiError && err.status === 403) {
        onForbidden()
        return
      }
      setBannerTone("destructive")
      setBanner(err instanceof ApiError ? err.message : t("roster:single.addFailedRetry"))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Card className="max-w-xl">
      <CardHeader>
        <CardTitle>{t("roster:single.title")}</CardTitle>
      </CardHeader>
      <CardContent>
        <form className="space-y-4" onSubmit={(event) => void handleSubmit(event)}>
          <div className="space-y-2">
            <Label htmlFor="studentId">{t("roster:single.studentId")}</Label>
            <Input
              id="studentId"
              name="studentId"
              value={values.studentId}
              inputMode="numeric"
              autoComplete="off"
              maxLength={ROSTER_FIELD_MAX.studentId}
              aria-invalid={Boolean(fieldErrors.studentId)}
              onChange={(event) => updateField("studentId", event.target.value)}
            />
            {fieldErrors.studentId ? (
              <p className="text-sm text-destructive">{fieldErrors.studentId}</p>
            ) : null}
          </div>

          <div className="space-y-2">
            <Label htmlFor="studentName">{t("roster:single.name")}</Label>
            <Input
              id="studentName"
              name="studentName"
              value={values.studentName}
              maxLength={ROSTER_FIELD_MAX.studentName}
              aria-invalid={Boolean(fieldErrors.studentName)}
              onChange={(event) => updateField("studentName", event.target.value)}
            />
            {fieldErrors.studentName ? (
              <p className="text-sm text-destructive">{fieldErrors.studentName}</p>
            ) : null}
          </div>

          <div className="space-y-2">
            <Label htmlFor="major">{t("roster:single.major")}</Label>
            <Input
              id="major"
              name="major"
              value={values.major}
              maxLength={ROSTER_FIELD_MAX.major}
              aria-invalid={Boolean(fieldErrors.major)}
              onChange={(event) => updateField("major", event.target.value)}
            />
            {fieldErrors.major ? (
              <p className="text-sm text-destructive">{fieldErrors.major}</p>
            ) : null}
          </div>

          <div className="space-y-2">
            <Label htmlFor="className">{t("roster:single.class")}</Label>
            <Input
              id="className"
              name="className"
              value={values.className}
              maxLength={ROSTER_FIELD_MAX.className}
              aria-invalid={Boolean(fieldErrors.className)}
              onChange={(event) => updateField("className", event.target.value)}
            />
            {fieldErrors.className ? (
              <p className="text-sm text-destructive">{fieldErrors.className}</p>
            ) : null}
          </div>

          <div className="space-y-2">
            <Label htmlFor="semester">{t("roster:single.semester")}</Label>
            <Input
              id="semester"
              name="semester"
              value={values.semester}
              maxLength={ROSTER_FIELD_MAX.semester}
              aria-invalid={Boolean(fieldErrors.semester)}
              onChange={(event) => updateField("semester", event.target.value)}
            />
            {fieldErrors.semester ? (
              <p className="text-sm text-destructive">{fieldErrors.semester}</p>
            ) : null}
            <p className="text-sm text-muted-foreground">
              {t("roster:single.classHint")}
            </p>
          </div>

          <div className="space-y-2">
            <Label htmlFor="initialPassword">{t("roster:single.initialPassword")}</Label>
            <Input
              id="initialPassword"
              name="initialPassword"
              type="text"
              value={values.initialPassword}
              maxLength={ROSTER_FIELD_MAX.initialPassword}
              autoComplete="new-password"
              onChange={(event) => updateField("initialPassword", event.target.value)}
            />
            <p className="text-sm text-muted-foreground">
              {t("roster:single.passwordHint")}
            </p>
          </div>

          {banner ? (
            <p className={cn("text-sm", bannerTone === "destructive" && "text-destructive")}>{banner}</p>
          ) : null}

          <div className="flex flex-wrap items-center gap-2">
            <Button type="submit" disabled={submitting}>
              {submitting ? t("roster:single.adding") : t("roster:single.submit")}
            </Button>
            <Button
              type="button"
              variant="outline"
              disabled={submitting}
              onClick={() => {
                setValues(EMPTY_FORM)
                setFieldErrors({})
                setBanner(null)
                onCancel()
              }}
            >
              {t("cancel")}
            </Button>
          </div>
        </form>
      </CardContent>
    </Card>
  )
}
