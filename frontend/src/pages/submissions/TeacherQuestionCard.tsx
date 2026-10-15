import { useState } from "react"
import { useTranslation } from "react-i18next"

import { MarkdownPreview } from "@/components/questions/MarkdownPreview"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Label } from "@/components/ui/label"
import { Textarea } from "@/components/ui/textarea"
import {
  formatAwardedScore,
  type StudentImageAnswer,
} from "@/lib/api/student-experiments"
import type {
  QuestionStatus,
  TeacherQuestionDetail,
  TeacherSubQuestionDetail,
} from "@/lib/api/teacher-submissions"
import { cn } from "@/lib/utils"

import { ForwardDisputesDialog } from "@/pages/disputes/ForwardDisputesDialog"
import { ResolveDisputeDialog } from "@/pages/submissions/ResolveDisputeDialog"

interface TeacherQuestionCardProps {
  question: TeacherQuestionDetail
  questionIndex: number
  textValues: Record<number, string>
  imageValues: Record<number, StudentImageAnswer | null>
  highlighted?: boolean
  onResolved?: () => void | Promise<void>
}

function ReadOnlyImage({ image }: { image: StudentImageAnswer | null }) {
  const { t } = useTranslation()
  if (!image) {
    return <p className="text-sm text-muted-foreground">{t("submissions:detail.unanswered")}</p>
  }
  return (
    <img
      src={image.url}
      alt={t("imageAlt")}
      className="max-h-64 max-w-full rounded-md border object-contain"
    />
  )
}

function subQuestionScoreLabel(sub: TeacherSubQuestionDetail): string {
  return ` · ${formatAwardedScore(sub.effectiveScore)} / ${sub.maxScore}`
}

function TeacherInternals({ sub }: { sub: TeacherSubQuestionDetail }) {
  const { t } = useTranslation()
  const comment = sub.aiEvidence.trim()
  const referenceAnswer = sub.referenceAnswer.trim()
  const rubric = sub.rubric.trim()
  const gradingLog = sub.gradingLog.trim()

  return (
    <>
      {comment ? (
        <div className="space-y-1 rounded-md bg-muted/40 px-3 py-2">
          <p className="text-xs font-medium text-muted-foreground">{t("submissions:detail.comment")}</p>
          <p className="whitespace-pre-wrap text-sm">{comment}</p>
        </div>
      ) : null}

      {referenceAnswer ? (
        <div className="space-y-1">
          <p className="text-xs font-medium text-muted-foreground">{t("submissions:detail.referenceAnswer")}</p>
          <MarkdownPreview content={referenceAnswer} className="border-0 bg-muted/40 px-3 py-2" />
        </div>
      ) : null}

      {rubric ? (
        <div className="space-y-1 rounded-md bg-muted/40 px-3 py-2">
          <p className="text-xs font-medium text-muted-foreground">{t("submissions:detail.rubric")}</p>
          <p className="whitespace-pre-wrap text-sm">{rubric}</p>
        </div>
      ) : null}

      {sub.usedSandbox ? (
        <p className="text-xs text-muted-foreground">{t("submissions:detail.sandboxUsed")}</p>
      ) : null}

      {gradingLog ? (
        <details className="rounded-md bg-muted/40 px-3 py-2">
          <summary className="cursor-pointer text-xs font-medium text-muted-foreground">
            {t("submissions:detail.gradeLog")}
          </summary>
          <pre className="mt-2 max-h-64 overflow-auto whitespace-pre-wrap text-xs">{gradingLog}</pre>
        </details>
      ) : null}
    </>
  )
}

export function TeacherQuestionCard({
  question,
  questionIndex,
  textValues,
  imageValues,
  highlighted = false,
  onResolved,
}: TeacherQuestionCardProps) {
  const { t } = useTranslation()
  const [resolveOpen, setResolveOpen] = useState(false)
  const [forwardOpen, setForwardOpen] = useState(false)
  const questionStatus: QuestionStatus | null = question.status
  const headerScore =
    questionStatus === 2 && question.score != null
      ? `${formatAwardedScore(question.score)} / ${question.effectivePoints}`
      : t("points", { count: question.effectivePoints })
  const olderDisputes = question.disputes.slice(1)
  const latestRound = question.disputes[0]
  const latestWithdrawn =
    Boolean(latestRound?.withdrawnTime) || question.disputeStatus === 3
  const canResolve =
    question.disputeStatus === 1 &&
    latestRound != null &&
    latestRound.id > 0 &&
    !latestRound.withdrawnTime &&
    !latestRound.resolveTime
  const showDispute =
    question.disputeStatus === 1 ||
    question.disputeStatus === 2 ||
    question.disputeStatus === 3 ||
    question.disputes.length > 0

  return (
    <section
      id={`question-${question.experimentQuestionId}`}
      className={cn(
        "space-y-6 rounded-xl border bg-card px-4 py-5",
        highlighted && "ring-2 ring-primary/40",
      )}
    >
      <header className="space-y-2">
        <div className="flex flex-wrap items-center gap-2">
          <h2 className="text-lg font-semibold">
            {t("submissions:detail.question", { index: questionIndex + 1, title: question.questionTitle || t("emptyTitle") })}
          </h2>
          <span className="text-sm text-muted-foreground">{headerScore}</span>
          {questionStatus === 3 ? (
            <span className="text-sm text-muted-foreground">{t("submissions:detail.gradingFailed")}</span>
          ) : null}
        </div>
        {showDispute ? (
          <div className="space-y-2">
            {latestWithdrawn ? (
              <Badge className="border-transparent bg-muted text-muted-foreground">{t("submissions:detail.withdrawn")}</Badge>
            ) : question.disputeStatus === 1 || question.disputeStatus === 2 ? (
              <div className="flex flex-wrap items-center gap-2">
                <Badge
                  className={
                    question.disputeStatus === 1
                      ? "border-transparent bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-400"
                      : "border-transparent bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400"
                  }
                >
                  {question.disputeStatus === 1 ? t("submissions:detail.reviewRequested") : t("submissions:detail.resolved")}
                </Badge>
                {canResolve ? (
                  <>
                    <Button type="button" size="sm" onClick={() => setResolveOpen(true)}>
                      {t("submissions:detail.resolve")}
                    </Button>
                    <Button
                      type="button"
                      size="sm"
                      variant="outline"
                      onClick={() => setForwardOpen(true)}
                    >
                      {t("submissions:detail.forwardAi")}
                    </Button>
                  </>
                ) : null}
              </div>
            ) : null}
            {question.disputeReason ? (
              <div className="space-y-1 rounded-md bg-muted/40 px-3 py-2">
                <p className="text-xs font-medium text-muted-foreground">{t("submissions:detail.disputeReason")}</p>
                <p className="whitespace-pre-wrap text-sm">{question.disputeReason}</p>
              </div>
            ) : null}
            {!latestWithdrawn && question.disputeStatus === 2 && question.manualComment ? (
              <div className="space-y-1 rounded-md bg-muted/40 px-3 py-2">
                <p className="text-xs font-medium text-muted-foreground">{t("submissions:detail.teacherReply")}</p>
                <p className="whitespace-pre-wrap text-sm">{question.manualComment}</p>
              </div>
            ) : null}
            {olderDisputes.length > 0 ? (
              <details className="rounded-md bg-muted/40 px-3 py-2">
                <summary className="cursor-pointer text-xs font-medium text-muted-foreground">
                  {t("submissions:detail.historyDisputes")}
                </summary>
                <div className="mt-2 space-y-3">
                  {olderDisputes.map((round) => (
                    <div key={round.id} className="space-y-1 text-sm">
                      <p className="text-xs text-muted-foreground">
                        {round.disputeTime}
                        {round.withdrawnTime ? (
                          <span className="ml-2 text-muted-foreground">{t("submissions:detail.withdrawn")}</span>
                        ) : null}
                      </p>
                      {round.disputeReason ? (
                        <p className="whitespace-pre-wrap">{round.disputeReason}</p>
                      ) : null}
                      {round.manualComment ? (
                        <p className="whitespace-pre-wrap text-muted-foreground">{round.manualComment}</p>
                      ) : null}
                    </div>
                  ))}
                </div>
              </details>
            ) : null}
          </div>
        ) : null}
        {question.questionContent.trim() ? (
          <MarkdownPreview content={question.questionContent} />
        ) : (
          <p className="text-sm text-muted-foreground">{t("submissions:detail.noQuestionContent")}</p>
        )}
      </header>

      <div className="space-y-5">
        {question.subQuestions.length === 0 ? (
          <p className="text-sm text-muted-foreground">{t("submissions:detail.noSubQuestions")}</p>
        ) : (
          question.subQuestions.map((subQuestion, subIndex) => {
            const text = textValues[subQuestion.subQuestionId] ?? ""
            return (
              <div key={subQuestion.subQuestionId} className="space-y-3 rounded-lg border px-4 py-4">
                <div className="space-y-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <h3 className="font-medium">{t("submissions:detail.subQuestion", { index: subIndex + 1 })}</h3>
                    <span className="text-xs text-muted-foreground">
                      {t(`questions:answerFormat.${subQuestion.answerFormat}`)}
                      {subQuestionScoreLabel(subQuestion)}
                    </span>
                  </div>
                  {subQuestion.content.trim() ? (
                    <MarkdownPreview
                      content={subQuestion.content}
                      className="border-0 bg-transparent px-0 py-0"
                    />
                  ) : (
                    <p className="text-sm text-muted-foreground">{t("submissions:detail.noContent")}</p>
                  )}
                </div>

                <div className="space-y-2">
                  <Label>{t("submissions:detail.studentAnswer")}</Label>
                  {subQuestion.answerFormat === "image" ? (
                    <ReadOnlyImage image={imageValues[subQuestion.subQuestionId] ?? null} />
                  ) : (
                    <Textarea
                      value={text}
                      readOnly
                      disabled
                      placeholder={t("submissions:detail.unanswered")}
                      className={
                        subQuestion.answerFormat === "code"
                          ? "min-h-28 font-mono text-sm"
                          : "min-h-28"
                      }
                    />
                  )}
                  <TeacherInternals sub={subQuestion} />
                </div>
              </div>
            )
          })
        )}
      </div>
      <ResolveDisputeDialog
        open={resolveOpen}
        onOpenChange={setResolveOpen}
        disputeId={latestRound?.id ?? null}
        subQuestions={question.subQuestions}
        onResolved={() => onResolved?.()}
      />
      <ForwardDisputesDialog
        open={forwardOpen}
        onOpenChange={setForwardOpen}
        disputeIds={latestRound?.id ? [latestRound.id] : []}
        onForwarded={() => onResolved?.()}
      />
    </section>
  )
}
