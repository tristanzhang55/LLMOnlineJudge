import { useEffect, useMemo, useState } from "react"
import { useTranslation } from "react-i18next"

import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { formatClassLabel, type ClassOption } from "@/lib/api/classes"
import { ApiError } from "@/lib/api/client"
import {
  ROSTER_FIELD_MAX,
  updateRosterEnrollment,
  type RosterEnrollment,
} from "@/lib/api/teacher-roster"

interface RosterEditDialogProps {
  open: boolean
  enrollment: RosterEnrollment | null
  classes: ClassOption[]
  onOpenChange: (open: boolean) => void
  onSaved: () => void | Promise<void>
  onStale: () => void | Promise<void>
}

function semesterKey(value: string | null | undefined): string {
  return value?.trim() ?? ""
}

function classOptionsForRow(classes: ClassOption[], row: RosterEnrollment): ClassOption[] {
  const key = semesterKey(row.semester)
  const options = classes.filter((item) => semesterKey(item.semester) === key)
  if (!options.some((item) => item.id === row.classId)) {
    return [
      {
        id: row.classId,
        name: row.className,
        semester: row.semester,
        createTime: null,
      },
      ...options,
    ]
  }
  return options
}

export function RosterEditDialog({
  open,
  enrollment,
  classes,
  onOpenChange,
  onSaved,
  onStale,
}: RosterEditDialogProps) {
  const { t } = useTranslation()
  const [studentName, setStudentName] = useState("")
  const [major, setMajor] = useState("")
  const [classId, setClassId] = useState("")
  const [fieldErrors, setFieldErrors] = useState<{
    studentName?: string
    major?: string
    classId?: string
  }>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    if (!open || !enrollment) {
      return
    }
    setStudentName(enrollment.studentName)
    setMajor(enrollment.major ?? "")
    setClassId(String(enrollment.classId))
    setFieldErrors({})
    setFormError(null)
  }, [open, enrollment])

  const classOptions = useMemo(() => {
    if (!enrollment) {
      return []
    }
    return classOptionsForRow(classes, enrollment)
  }, [classes, enrollment])

  const clientInvalid = !studentName.trim() || !major.trim() || !classId
  const submitDisabled = submitting || clientInvalid || enrollment == null

  function closeIfIdle(nextOpen: boolean) {
    if (!submitting) {
      onOpenChange(nextOpen)
    }
  }

  async function handleSubmit() {
    if (submitDisabled || enrollment == null) {
      const nextErrors: typeof fieldErrors = {}
      if (!studentName.trim()) {
        nextErrors.studentName = t("roster:edit.required")
      }
      if (!major.trim()) {
        nextErrors.major = t("roster:edit.required")
      }
      setFieldErrors(nextErrors)
      return
    }

    setSubmitting(true)
    setFormError(null)
    setFieldErrors({})
    try {
      await updateRosterEnrollment(enrollment.classStudentId, {
        studentName: studentName.trim(),
        major: major.trim(),
        classId: Number(classId),
      })
      onOpenChange(false)
      await onSaved()
    } catch (err) {
      if (err instanceof ApiError && err.status === 404) {
        onOpenChange(false)
        await onStale()
        return
      }
      if (err instanceof ApiError && (err.status === 400 || err.status === 409)) {
        const nextErrors: typeof fieldErrors = {}
        for (const item of err.fieldErrors ?? []) {
          if (item.field === "studentName") {
            nextErrors.studentName = item.message
          } else if (item.field === "major") {
            nextErrors.major = item.message
          } else if (item.field === "classId") {
            nextErrors.classId = item.message
          }
        }
        if (err.status === 409 && !nextErrors.classId) {
          nextErrors.classId = t("roster:edit.alreadyInClass")
        }
        setFieldErrors(nextErrors)
        if (Object.keys(nextErrors).length === 0) {
          setFormError(err.message || t("roster:edit.saveFailed"))
        }
        return
      }
      setFormError(err instanceof ApiError ? err.message : t("roster:edit.saveFailed"))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={closeIfIdle}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{t("roster:edit.title")}</DialogTitle>
          <DialogDescription>
            {t("roster:edit.hint")}
          </DialogDescription>
        </DialogHeader>
        {enrollment ? (
          <div className="space-y-4">
            <div className="space-y-1">
              <Label>{t("roster:edit.studentId")}</Label>
              <p className="text-sm">{enrollment.studentId}</p>
            </div>
            <div className="space-y-1">
              <Label>{t("roster:edit.semester")}</Label>
              <p className="text-sm">{enrollment.semester ?? "—"}</p>
            </div>
            <div className="space-y-2">
              <Label htmlFor="roster-edit-name">{t("roster:edit.name")}</Label>
              <Input
                id="roster-edit-name"
                value={studentName}
                maxLength={ROSTER_FIELD_MAX.studentName}
                aria-invalid={Boolean(fieldErrors.studentName)}
                onChange={(event) => setStudentName(event.target.value)}
              />
              {fieldErrors.studentName ? (
                <p className="text-sm text-destructive">{fieldErrors.studentName}</p>
              ) : null}
            </div>
            <div className="space-y-2">
              <Label htmlFor="roster-edit-major">{t("roster:edit.major")}</Label>
              <Input
                id="roster-edit-major"
                value={major}
                maxLength={ROSTER_FIELD_MAX.major}
                aria-invalid={Boolean(fieldErrors.major)}
                onChange={(event) => setMajor(event.target.value)}
              />
              {fieldErrors.major ? (
                <p className="text-sm text-destructive">{fieldErrors.major}</p>
              ) : null}
            </div>
            <div className="space-y-2">
              <Label>{t("roster:edit.class")}</Label>
              <Select value={classId} onValueChange={(value) => setClassId(value ?? classId)}>
                <SelectTrigger className="w-full" aria-invalid={Boolean(fieldErrors.classId)}>
                  <SelectValue placeholder={t("roster:edit.selectClass")} />
                </SelectTrigger>
                <SelectContent>
                  {classOptions.map((item) => (
                    <SelectItem key={item.id} value={String(item.id)}>
                      {formatClassLabel(item)}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              {fieldErrors.classId ? (
                <p className="text-sm text-destructive">{fieldErrors.classId}</p>
              ) : null}
            </div>
            {formError ? <p className="text-sm text-destructive">{formError}</p> : null}
          </div>
        ) : null}
        <DialogFooter>
          <Button type="button" variant="outline" disabled={submitting} onClick={() => closeIfIdle(false)}>
            {t("cancel")}
          </Button>
          <Button type="button" disabled={submitDisabled} onClick={() => void handleSubmit()}>
            {submitting ? t("saving") : t("save")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
