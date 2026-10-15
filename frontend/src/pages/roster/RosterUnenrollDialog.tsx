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
import { deleteRosterEnrollment, type RosterEnrollment } from "@/lib/api/teacher-roster"

interface RosterUnenrollDialogProps {
  open: boolean
  enrollment: RosterEnrollment | null
  onOpenChange: (open: boolean) => void
  onRemoved: () => void | Promise<void>
  onStale: () => void | Promise<void>
  onError: (message: string) => void
}

export function RosterUnenrollDialog({
  open,
  enrollment,
  onOpenChange,
  onRemoved,
  onStale,
  onError,
}: RosterUnenrollDialogProps) {
  const { t } = useTranslation()
  const [submitting, setSubmitting] = useState(false)

  async function handleConfirm() {
    if (!enrollment || submitting) {
      return
    }
    setSubmitting(true)
    try {
      await deleteRosterEnrollment(enrollment.classStudentId)
      onOpenChange(false)
      await onRemoved()
    } catch (err) {
      onOpenChange(false)
      if (err instanceof ApiError && err.status === 404) {
        await onStale()
        return
      }
      onError(err instanceof ApiError ? err.message : t("roster:unenroll.failed"))
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
          <AlertDialogTitle>{t("roster:unenroll.title")}</AlertDialogTitle>
          <AlertDialogDescription>
            {t("roster:unenroll.body", {
              name: enrollment?.studentName ?? "",
              className: enrollment?.className ?? "",
            })}
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel disabled={submitting}>{t("cancel")}</AlertDialogCancel>
          <AlertDialogAction
            type="button"
            variant="destructive"
            disabled={submitting}
            onClick={(event) => {
              event.preventDefault()
              void handleConfirm()
            }}
          >
            {submitting ? t("roster:unenroll.removing") : t("roster:unenroll.confirm")}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}
