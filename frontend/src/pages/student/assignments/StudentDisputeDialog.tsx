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
import { Textarea } from "@/components/ui/textarea"
import { ApiError } from "@/lib/api/client"
import { fileStudentDispute } from "@/lib/api/student-experiments"

const MAX_REASON = 1024

interface StudentDisputeDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  experimentId: number
  submissionId: number
  onFiled: () => void | Promise<void>
}

export function StudentDisputeDialog({
  open,
  onOpenChange,
  experimentId,
  submissionId,
  onFiled,
}: StudentDisputeDialogProps) {
  const { t } = useTranslation()
  const [reason, setReason] = useState("")
  const [fieldError, setFieldError] = useState<string | null>(null)
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const trimmed = reason.trim()
  const overLimit = reason.length > MAX_REASON
  const submitDisabled = trimmed.length === 0 || overLimit || submitting

  function resetAndClose(nextOpen: boolean) {
    if (!nextOpen && !submitting) {
      setReason("")
      setFieldError(null)
      setFormError(null)
    }
    if (!submitting) {
      onOpenChange(nextOpen)
    }
  }

  async function handleSubmit() {
    if (submitDisabled) {
      return
    }

    setSubmitting(true)
    setFieldError(null)
    setFormError(null)

    try {
      await fileStudentDispute(experimentId, submissionId, trimmed)
      setReason("")
      onOpenChange(false)
      await onFiled()
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        setFormError(err.message || t("student:dispute.inProgress"))
        await onFiled()
        onOpenChange(false)
        setReason("")
        return
      }
      if (err instanceof ApiError && err.status === 400) {
        const reasonError = err.fieldErrors?.find((item) => item.field === "disputeReason")
        if (reasonError) {
          setFieldError(reasonError.message)
        } else {
          setFormError(err.message)
        }
        return
      }
      if (err instanceof ApiError) {
        setFormError(err.message || t("student:dispute.failed"))
      } else {
        setFormError(t("student:dispute.failed"))
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={resetAndClose}>
      <DialogContent className="sm:max-w-lg" showCloseButton>
        <DialogHeader>
          <DialogTitle>{t("student:dispute.title")}</DialogTitle>
          <DialogDescription>
            {t("student:dispute.hint")}
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-2">
          <Textarea
            value={reason}
            onChange={(event) => setReason(event.target.value)}
            placeholder={t("student:dispute.placeholder")}
            className="min-h-32"
            maxLength={MAX_REASON}
          />
          <div className="flex items-center justify-between text-xs text-muted-foreground">
            <span>
              {fieldError ? <span className="text-destructive">{fieldError}</span> : null}
            </span>
            <span>
              {reason.length} / {MAX_REASON}
            </span>
          </div>
          {formError ? <p className="text-sm text-destructive">{formError}</p> : null}
        </div>
        <DialogFooter>
          <Button type="button" variant="outline" disabled={submitting} onClick={() => resetAndClose(false)}>
            {t("cancel")}
          </Button>
          <Button type="button" disabled={submitDisabled} onClick={() => void handleSubmit()}>
            {submitting ? t("student:dispute.submitting") : t("student:dispute.submit")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
