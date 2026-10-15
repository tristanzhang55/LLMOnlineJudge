import { useRef, useState } from "react"
import { useTranslation } from "react-i18next"
import type { TFunction } from "i18next"
import { MarkdownPreview } from "@/components/questions/MarkdownPreview"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Label } from "@/components/ui/label"
import { Textarea } from "@/components/ui/textarea"
import { IMAGE_ACCEPT } from "@/hooks/useMarkdownImageUpload"
import {
  canFileDispute,
  formatAwardedScore,
  type DisputeStatus,
  type MyQuestionScores,
  type MySubQuestionScore,
  type StudentImageAnswer,
  type StudentExperimentQuestion,
} from "@/lib/api/student-experiments"
import { cn } from "@/lib/utils"

import { StudentDisputeDialog } from "@/pages/student/assignments/StudentDisputeDialog"
import { WithdrawDisputeDialog } from "@/pages/student/disputes/WithdrawDisputeDialog"

interface StudentQuestionAnswerProps {
  question: StudentExperimentQuestion
  questionIndex: number
  editable: boolean
  values: Record<number, string>
  imageValues: Record<number, StudentImageAnswer | null>
  onChange: (subQuestionId: number, content: string) => void
  onBlurQuestion: () => void
  onImageUpload: (subQuestionId: number, file: File) => void | Promise<void>
  onImageRemove?: (subQuestionId: number) => void
  uploadingSubQuestionId?: number | null
  questionScore?: number | null
  questionStatus?: MyQuestionScores["status"]
  subQuestionScores?: Record<number, MySubQuestionScore>
  experimentId?: number
  submissionId?: number | null
  disputeStatus?: DisputeStatus
  disputeId?: number | null
  disputeReason?: string | null
  manualComment?: string | null
  highlighted?: boolean
  onDisputeFiled?: () => void | Promise<void>
  onWithdrawn?: () => void | Promise<void>
}

function ImageAnswer({
  image,
  editable,
  uploading,
  onFile,
  onRemove,
}: {
  image: StudentImageAnswer | null
  editable: boolean
  uploading: boolean
  onFile: (file: File) => void
  onRemove?: () => void
}) {
  const { t } = useTranslation()
  const fileInputRef = useRef<HTMLInputElement>(null)

  if (!editable) {
    if (!image) {
      return <p className="text-sm text-muted-foreground">{t("student:answer.unanswered")}</p>
    }
    return (
      <img
        src={image.url}
        alt={t("imageAlt")}
        className="max-h-64 max-w-full rounded-md border object-contain"
      />
    )
  }

  return (
    <div className="space-y-3">
      {image ? (
        <img
          src={image.url}
          alt={t("imageAlt")}
          className="max-h-64 max-w-full rounded-md border object-contain"
        />
      ) : null}
      <input
        ref={fileInputRef}
        type="file"
        accept={IMAGE_ACCEPT}
        className="hidden"
        disabled={uploading}
        onChange={(event) => {
          const file = event.target.files?.[0]
          event.target.value = ""
          if (file) {
            onFile(file)
          }
        }}
      />
      <div className="flex flex-wrap items-center gap-2">
        <Button
          type="button"
          variant="outline"
          size="sm"
          disabled={uploading}
          onClick={() => fileInputRef.current?.click()}
        >
          {uploading ? t("uploading") : image ? t("replaceImage") : t("uploadImage")}
        </Button>
        {image && onRemove ? (
          <Button type="button" variant="ghost" size="sm" disabled={uploading} onClick={onRemove}>
            {t("student:answer.remove")}
          </Button>
        ) : null}
      </div>
    </div>
  )
}

function subQuestionScoreLabel(
  subQuestionScore: number,
  overlay: MySubQuestionScore | undefined,
  t: TFunction,
): string {
  if (!overlay) {
    return typeof subQuestionScore === "number" ? ` · ${t("points", { count: subQuestionScore })}` : ""
  }
  const max = overlay.maxScore
  return ` · ${formatAwardedScore(overlay.effectiveScore)} / ${max}`
}

const CARD_DISPUTE_BADGE_CLASS: Record<2 | 3, string> = {
  2: "border-transparent bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400",
  3: "border-transparent bg-muted text-muted-foreground",
}

export function StudentQuestionAnswer({
  question,
  questionIndex,
  editable,
  values,
  imageValues,
  onChange,
  onBlurQuestion,
  onImageUpload,
  onImageRemove,
  uploadingSubQuestionId = null,
  questionScore = null,
  questionStatus = null,
  subQuestionScores = {},
  experimentId,
  submissionId = null,
  disputeStatus = 0,
  disputeId = null,
  disputeReason = null,
  manualComment = null,
  highlighted = false,
  onDisputeFiled,
  onWithdrawn,
}: StudentQuestionAnswerProps) {
  const { t } = useTranslation()
  const [dialogOpen, setDialogOpen] = useState(false)
  const [withdrawOpen, setWithdrawOpen] = useState(false)
  const headerScore =
    questionStatus === 2 && questionScore != null
      ? `${formatAwardedScore(questionScore)} / ${question.effectivePoints}`
      : t("points", { count: question.effectivePoints })
  const showFile =
    experimentId != null &&
    onDisputeFiled != null &&
    canFileDispute(questionStatus ?? null, disputeStatus, submissionId)
  const canWithdraw = disputeStatus === 1 && disputeId != null && onWithdrawn != null
  const showDisputeBlock =
    disputeStatus === 1 || disputeStatus === 2 || disputeStatus === 3 || showFile

  return (
    <section
      id={`question-${question.id}`}
      className={cn(
        "space-y-6 rounded-xl border bg-card px-4 py-5",
        highlighted && "ring-2 ring-primary/40",
      )}
      onBlur={(event) => {
        const next = event.relatedTarget
        if (next instanceof Node && event.currentTarget.contains(next)) {
          return
        }
        onBlurQuestion()
      }}
    >
      <header className="space-y-2">
        <div className="flex flex-wrap items-center gap-2">
          <h2 className="text-lg font-semibold">
            {t("student:answer.question", { index: questionIndex + 1, title: question.questionTitle || t("emptyTitle") })}
          </h2>
          <span className="text-sm text-muted-foreground">{headerScore}</span>
          {questionStatus === 3 ? (
            <span className="text-sm text-muted-foreground">{t("student:answer.gradingFailed")}</span>
          ) : null}
        </div>
        {question.questionContent.trim() ? (
          <MarkdownPreview content={question.questionContent} />
        ) : (
          <p className="text-sm text-muted-foreground">{t("student:answer.noQuestionContent")}</p>
        )}
      </header>

      <div className="space-y-5">
        {question.subQuestions.length === 0 ? (
          <p className="text-sm text-muted-foreground">{t("student:answer.noSubQuestions")}</p>
        ) : (
          question.subQuestions.map((subQuestion, subIndex) => {
            const overlay = subQuestionScores[subQuestion.id]
            const comment = overlay?.comment.trim() ?? ""
            return (
              <div key={subQuestion.id} className="space-y-3 rounded-lg border px-4 py-4">
                <div className="space-y-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <h3 className="font-medium">{t("student:answer.subQuestion", { index: subIndex + 1 })}</h3>
                    <span className="text-xs text-muted-foreground">
                      {t(`questions:answerFormat.${subQuestion.answerFormat}`)}
                      {subQuestionScoreLabel(subQuestion.score, overlay, t)}
                    </span>
                  </div>
                  {subQuestion.content.trim() ? (
                    <MarkdownPreview
                      content={subQuestion.content}
                      className="border-0 bg-transparent px-0 py-0"
                    />
                  ) : (
                    <p className="text-sm text-muted-foreground">{t("student:answer.noContent")}</p>
                  )}
                </div>

                <div className="space-y-2">
                  <Label>{t("student:answer.yourAnswer")}</Label>
                  {subQuestion.answerFormat === "image" ? (
                    <ImageAnswer
                      image={imageValues[subQuestion.id] ?? null}
                      editable={editable}
                      uploading={uploadingSubQuestionId === subQuestion.id}
                      onFile={(file) => void onImageUpload(subQuestion.id, file)}
                      onRemove={
                        onImageRemove ? () => onImageRemove(subQuestion.id) : undefined
                      }
                    />
                  ) : (
                    <Textarea
                      value={values[subQuestion.id] ?? ""}
                      onChange={(event) => onChange(subQuestion.id, event.target.value)}
                      disabled={!editable}
                      readOnly={!editable}
                      placeholder={
                        subQuestion.answerFormat === "code"
                          ? t("student:answer.codePlaceholder")
                          : t("student:answer.textPlaceholder")
                      }
                      className={
                        subQuestion.answerFormat === "code"
                          ? "min-h-28 font-mono text-sm"
                          : "min-h-28"
                      }
                    />
                  )}
                  {comment ? (
                    <div className="space-y-1 rounded-md bg-muted/40 px-3 py-2">
                      <p className="text-xs font-medium text-muted-foreground">{t("student:answer.comment")}</p>
                      <p className="whitespace-pre-wrap text-sm">{comment}</p>
                    </div>
                  ) : null}
                </div>
              </div>
            )
          })
        )}
      </div>
      {showDisputeBlock ? (
        <div className="space-y-2">
          {disputeReason ? (
            <div className="space-y-1 rounded-md bg-muted/40 px-3 py-2">
              <p className="text-xs font-medium text-muted-foreground">{t("student:answer.disputeReason")}</p>
              <p className="whitespace-pre-wrap text-sm">{disputeReason}</p>
            </div>
          ) : null}
          {disputeStatus === 2 && manualComment ? (
            <div className="space-y-1 rounded-md bg-muted/40 px-3 py-2">
              <p className="text-xs font-medium text-muted-foreground">{t("student:answer.teacherReply")}</p>
              <p className="whitespace-pre-wrap text-sm">{manualComment}</p>
            </div>
          ) : null}
          <div className="flex flex-wrap justify-center gap-2">
            {disputeStatus === 2 ? (
              <Badge className={CARD_DISPUTE_BADGE_CLASS[2]}>{t("disputes:listStatus.2")}</Badge>
            ) : null}
            {disputeStatus === 3 ? (
              <Badge className={CARD_DISPUTE_BADGE_CLASS[3]}>{t("disputes:listStatus.3")}</Badge>
            ) : null}
            {disputeStatus === 1 ? (
              <Button
                type="button"
                size="sm"
                disabled
                className="bg-blue-600 text-white hover:bg-blue-600 disabled:opacity-100"
              >
                {t("student:answer.reviewRequested")}
              </Button>
            ) : null}
            {canWithdraw ? (
              <Button type="button" variant="outline" size="sm" onClick={() => setWithdrawOpen(true)}>
                {t("student:answer.withdraw")}
              </Button>
            ) : null}
            {showFile ? (
              <Button
                type="button"
                size="sm"
                className="bg-red-600 text-white hover:bg-red-700"
                onClick={() => setDialogOpen(true)}
              >
                {t("student:answer.file")}
              </Button>
            ) : null}
          </div>
        </div>
      ) : null}
      {experimentId != null && submissionId != null && onDisputeFiled ? (
        <StudentDisputeDialog
          open={dialogOpen}
          onOpenChange={setDialogOpen}
          experimentId={experimentId}
          submissionId={submissionId}
          onFiled={onDisputeFiled}
        />
      ) : null}
      {canWithdraw ? (
        <WithdrawDisputeDialog
          open={withdrawOpen}
          onOpenChange={setWithdrawOpen}
          disputeId={disputeId}
          onWithdrawn={onWithdrawn}
        />
      ) : null}
    </section>
  )
}
