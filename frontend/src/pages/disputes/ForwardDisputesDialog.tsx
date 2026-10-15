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
import {
  FORWARD_BATCH_MAX,
  forwardTeacherDisputes,
  uniquePositiveIds,
  type ForwardDisputeResult,
} from "@/lib/api/teacher-disputes"

const MAX_NOTE = 1024

interface ForwardDisputesDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  disputeIds: number[]
  hasManualOverride?: boolean
  onForwarded: (results: ForwardDisputeResult[]) => void | Promise<void>
}

export function ForwardDisputesDialog({
  open,
  onOpenChange,
  disputeIds,
  hasManualOverride = false,
  onForwarded,
}: ForwardDisputesDialogProps) {
  const { t } = useTranslation()
  const [note, setNote] = useState("")
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const uniqueIds = uniquePositiveIds(disputeIds)
  const trimmed = note.trim()
  const overLimit = note.length > MAX_NOTE
  const overCap = uniqueIds.length > FORWARD_BATCH_MAX
  const submitDisabled =
    trimmed.length === 0 || overLimit || uniqueIds.length === 0 || overCap || submitting

  function resetAndClose(nextOpen: boolean) {
    if (!nextOpen && !submitting) {
      setNote("")
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
    setFormError(null)

    try {
      const results = await forwardTeacherDisputes(trimmed, uniqueIds)
      setNote("")
      onOpenChange(false)
      await onForwarded(results)
    } catch (err) {
      if (err instanceof ApiError) {
        setFormError(err.message || t("disputes:forward.failed"))
      } else {
        setFormError(t("disputes:forward.failed"))
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={resetAndClose}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{t("disputes:forward.title")}</DialogTitle>
          <DialogDescription>
            {t("disputes:forward.body")}
          </DialogDescription>
        </DialogHeader>
        {hasManualOverride ? (
          <p className="text-sm text-muted-foreground">
            {t("disputes:forward.manualWarning")}
          </p>
        ) : null}
        <div className="space-y-2">
          <Textarea
            value={note}
            onChange={(event) => setNote(event.target.value)}
            placeholder={t("disputes:forward.notePlaceholder")}
            className="min-h-28"
            maxLength={MAX_NOTE}
          />
          <div className="flex items-center justify-between text-xs text-muted-foreground">
            <span>
              {overCap ? <span className="text-destructive">{t("disputes:forward.overCap")}</span> : null}
            </span>
            <span>
              {note.length} / {MAX_NOTE}
            </span>
          </div>
          {formError ? <p className="text-sm text-destructive">{formError}</p> : null}
        </div>
        <DialogFooter>
          <Button type="button" variant="outline" disabled={submitting} onClick={() => resetAndClose(false)}>
            {t("cancel")}
          </Button>
          <Button type="button" disabled={submitDisabled} onClick={() => void handleSubmit()}>
            {submitting ? t("disputes:forward.forwarding") : t("disputes:forward.confirm")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
