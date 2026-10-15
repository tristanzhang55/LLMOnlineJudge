import { useState } from "react"
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
import { ApiError } from "@/lib/api/client"
import { withdrawStudentDispute } from "@/lib/api/student-disputes"

interface WithdrawDisputeDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  disputeId: number | null
  onWithdrawn: () => void | Promise<void>
  onError?: (message: string) => void
}

export function WithdrawDisputeDialog({
  open,
  onOpenChange,
  disputeId,
  onWithdrawn,
  onError,
}: WithdrawDisputeDialogProps) {
  const { t } = useTranslation()
  const [submitting, setSubmitting] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)

  function resetAndClose(nextOpen: boolean) {
    if (!nextOpen && !submitting) {
      setFormError(null)
    }
    if (!submitting) {
      onOpenChange(nextOpen)
    }
  }

  async function handleConfirm() {
    if (disputeId == null || submitting) {
      return
    }

    setSubmitting(true)
    setFormError(null)

    try {
      await withdrawStudentDispute(disputeId)
      setFormError(null)
      onOpenChange(false)
      await onWithdrawn()
    } catch (err) {
      if (err instanceof ApiError && (err.status === 409 || err.status === 404)) {
        const message =
          err.status === 409
            ? err.message || t("disputes:withdraw.alreadyHandled")
            : err.message || t("disputes:withdraw.notFound")
        setFormError(message)
        if (onError) {
          onError(message)
        } else {
          await onWithdrawn()
        }
        return
      }
      if (err instanceof ApiError) {
        setFormError(err.message || t("disputes:withdraw.failed"))
      } else {
        setFormError(t("disputes:withdraw.failed"))
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={resetAndClose}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{t("disputes:withdraw.title")}</DialogTitle>
          <DialogDescription>
            {t("disputes:withdraw.body")}
          </DialogDescription>
        </DialogHeader>
        {formError ? <p className="text-sm text-destructive">{formError}</p> : null}
        <DialogFooter className="border-t-0 bg-transparent p-0 pt-2 sm:justify-end">
          <Button
            type="button"
            variant="outline"
            disabled={submitting}
            onClick={() => resetAndClose(false)}
          >
            {t("cancel")}
          </Button>
          <Button type="button" disabled={submitting || disputeId == null} onClick={() => void handleConfirm()}>
            {submitting ? t("disputes:withdraw.withdrawing") : t("disputes:withdraw.confirm")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
