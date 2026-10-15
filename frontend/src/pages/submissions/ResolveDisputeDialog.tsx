import { useMemo, useState } from "react"
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
import { Textarea } from "@/components/ui/textarea"
import { ApiError } from "@/lib/api/client"
import { formatAwardedScore } from "@/lib/api/student-experiments"
import { resolveTeacherDispute } from "@/lib/api/teacher-disputes"
import type { TeacherSubQuestionDetail } from "@/lib/api/teacher-submissions"

const MAX_COMMENT = 1024

interface ResolveDisputeDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  disputeId: number | null
  subQuestions: TeacherSubQuestionDetail[]
  onResolved: () => void | Promise<void>
}

function parseWholeScore(
  raw: string,
  maxScore: number,
): { kind: "omit" } | { kind: "ok"; value: number } | { kind: "error" } {
  const trimmed = raw.trim()
  if (!trimmed) {
    return { kind: "omit" }
  }
  if (!/^\d+$/.test(trimmed)) {
    return { kind: "error" }
  }
  const value = Number(trimmed)
  if (!Number.isInteger(value) || value < 0 || value > maxScore) {
    return { kind: "error" }
  }
  return { kind: "ok", value }
}

export function ResolveDisputeDialog({
  open,
  onOpenChange,
  disputeId,
  subQuestions,
  onResolved,
}: ResolveDisputeDialogProps) {
  const { t } = useTranslation()
  const [comment, setComment] = useState("")
  const [scores, setScores] = useState<Record<number, string>>({})
  const [commentError, setCommentError] = useState<string | null>(null)
  const [scoreErrors, setScoreErrors] = useState<Record<number, string>>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const trimmed = comment.trim()
  const overLimit = comment.length > MAX_COMMENT
  const submitDisabled = trimmed.length === 0 || overLimit || submitting || disputeId == null

  const subQuestionIds = useMemo(
    () => new Set(subQuestions.map((item) => item.subQuestionId)),
    [subQuestions],
  )

  function resetAndClose(nextOpen: boolean) {
    if (!nextOpen && !submitting) {
      setComment("")
      setScores({})
      setCommentError(null)
      setScoreErrors({})
      setFormError(null)
    }
    if (!submitting) {
      onOpenChange(nextOpen)
    }
  }

  function applyFieldErrors(fieldErrors: { field: string; message: string }[] | undefined) {
    if (!fieldErrors?.length) {
      return false
    }
    let mapped = false
    const nextScoreErrors: Record<number, string> = {}
    for (const item of fieldErrors) {
      if (item.field === "manualComment") {
        setCommentError(item.message)
        mapped = true
        continue
      }
      const matchedId = [...subQuestionIds].find(
        (id) => item.field === String(id) || item.field.includes(String(id)),
      )
      if (matchedId != null) {
        nextScoreErrors[matchedId] = item.message
        mapped = true
      }
    }
    if (Object.keys(nextScoreErrors).length > 0) {
      setScoreErrors(nextScoreErrors)
    }
    return mapped
  }

  async function handleSubmit() {
    if (submitDisabled || disputeId == null) {
      return
    }

    const nextScoreErrors: Record<number, string> = {}
    const subQuestionScores: { subQuestionId: number; manualScore: number }[] = []
    for (const sub of subQuestions) {
      const parsed = parseWholeScore(scores[sub.subQuestionId] ?? "", sub.maxScore)
      if (parsed.kind === "error") {
        nextScoreErrors[sub.subQuestionId] = t("submissions:resolve.scoreRange", { max: sub.maxScore })
      } else if (parsed.kind === "ok") {
        subQuestionScores.push({ subQuestionId: sub.subQuestionId, manualScore: parsed.value })
      }
    }

    setCommentError(null)
    setScoreErrors(nextScoreErrors)
    setFormError(null)
    if (Object.keys(nextScoreErrors).length > 0) {
      return
    }

    setSubmitting(true)
    try {
      await resolveTeacherDispute(disputeId, {
        manualComment: trimmed,
        subQuestionScores,
      })
      setComment("")
      setScores({})
      onOpenChange(false)
      await onResolved()
    } catch (err) {
      if (err instanceof ApiError && (err.status === 409 || err.status === 404)) {
        setFormError(
          err.status === 409
            ? err.message || t("submissions:resolve.alreadyHandled")
            : err.message || t("submissions:resolve.notFound"),
        )
        await onResolved()
        return
      }
      if (err instanceof ApiError && err.status === 400) {
        const mapped = applyFieldErrors(err.fieldErrors)
        if (!mapped) {
          setFormError(err.message || t("submissions:resolve.failed"))
        }
        return
      }
      if (err instanceof ApiError) {
        setFormError(err.message || t("submissions:resolve.failed"))
      } else {
        setFormError(t("submissions:resolve.failed"))
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={resetAndClose}>
      <DialogContent className="max-h-[85vh] overflow-y-auto sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{t("submissions:resolve.title")}</DialogTitle>
          <DialogDescription>
            {t("submissions:resolve.hint")}
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-4">
          <div className="space-y-2">
            <Textarea
              value={comment}
              onChange={(event) => setComment(event.target.value)}
              placeholder={t("submissions:resolve.commentPlaceholder")}
              className="min-h-28"
              maxLength={MAX_COMMENT}
            />
            <div className="flex items-center justify-between text-xs text-muted-foreground">
              <span>
                {commentError ? <span className="text-destructive">{commentError}</span> : null}
              </span>
              <span>
                {comment.length} / {MAX_COMMENT}
              </span>
            </div>
          </div>
          {subQuestions.map((sub, index) => {
            const current =
              sub.effectiveScore == null
                ? "—"
                : `${formatAwardedScore(sub.effectiveScore)} / ${sub.maxScore}`
            return (
              <div key={sub.subQuestionId} className="space-y-1.5">
                <p className="text-sm">
                  {t("submissions:resolve.subQuestion", { index: index + 1 })}
                  <span className="ml-2 text-xs text-muted-foreground">{t("currentPoints", { score: current })}</span>
                </p>
                <Input
                  type="number"
                  min={0}
                  max={sub.maxScore}
                  step="1"
                  placeholder={t("submissions:resolve.scorePlaceholder")}
                  value={scores[sub.subQuestionId] ?? ""}
                  onChange={(event) =>
                    setScores((currentScores) => ({
                      ...currentScores,
                      [sub.subQuestionId]: event.target.value,
                    }))
                  }
                />
                {scoreErrors[sub.subQuestionId] ? (
                  <p className="text-xs text-destructive">{scoreErrors[sub.subQuestionId]}</p>
                ) : null}
              </div>
            )
          })}
          {formError ? <p className="text-sm text-destructive">{formError}</p> : null}
        </div>
        <DialogFooter>
          <Button type="button" variant="outline" disabled={submitting} onClick={() => resetAndClose(false)}>
            {t("cancel")}
          </Button>
          <Button type="button" disabled={submitDisabled} onClick={() => void handleSubmit()}>
            {submitting ? t("submissions:resolve.resolving") : t("submissions:resolve.confirm")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
