import { useState } from "react"
import { useTranslation } from "react-i18next"

import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog"
import { ApiError } from "@/lib/api/client"
import {
  ROSTER_RESET_PASSWORD,
  resetRosterPassword,
  type RosterEnrollment,
} from "@/lib/api/teacher-roster"

interface RosterResetPasswordDialogProps {
  open: boolean
  enrollment: RosterEnrollment | null
  onOpenChange: (open: boolean) => void
  onReset: (studentName: string) => void | Promise<void>
  onStale: () => void | Promise<void>
  onError: (message: string) => void
}

export function RosterResetPasswordDialog({
  open,
  enrollment,
  onOpenChange,
  onReset,
  onStale,
  onError,
}: RosterResetPasswordDialogProps) {
  const { t } = useTranslation()
  const [submitting, setSubmitting] = useState(false)

  async function handleConfirm() {
    if (!enrollment || submitting) {
      return
    }
    setSubmitting(true)
    try {
      await resetRosterPassword(enrollment.classStudentId)
      const name = enrollment.studentName
      onOpenChange(false)
      await onReset(name)
    } catch (err) {
      onOpenChange(false)
      if (err instanceof ApiError && err.status === 404) {
        await onStale()
        return
      }
      onError(err instanceof ApiError ? err.message : t("roster:reset.failed"))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AlertDialog
      open={open}
      onOpenChange={(nextOpen) => {
        if (!submitting) {
          onOpenChange(nextOpen)
        }
      }}
    >
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>{t("roster:reset.title")}</AlertDialogTitle>
          <AlertDialogDescription>
            {t("roster:reset.body", {
              name: enrollment?.studentName ?? "",
              studentId: enrollment?.studentId ?? "",
              password: ROSTER_RESET_PASSWORD,
            })}
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel disabled={submitting}>{t("cancel")}</AlertDialogCancel>
          <AlertDialogAction
            type="button"
            disabled={submitting}
            onClick={(event) => {
              event.preventDefault()
              void handleConfirm()
            }}
          >
            {submitting ? t("roster:reset.resetting") : t("roster:reset.confirm")}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}
