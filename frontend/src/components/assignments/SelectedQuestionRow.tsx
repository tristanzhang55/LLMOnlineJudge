import { useEffect, useState } from "react"
import { Eye, GripVertical, Trash2 } from "lucide-react"
import { useTranslation } from "react-i18next"

import { QuestionStudentView } from "@/components/questions/QuestionStudentView"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import type { SelectedQuestionFormValue } from "@/lib/mock-assignments"
import { getQuestionById } from "@/lib/api/questions"
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from "@/components/ui/tooltip"
import { cn } from "@/lib/utils"

interface SelectedQuestionRowProps {
  index: number
  value: SelectedQuestionFormValue
  error?: string
  onChange: (value: SelectedQuestionFormValue) => void
  onRemove: () => void
  onDragStart: () => void
  onDragOver: (event: React.DragEvent) => void
  onDrop: () => void
  isDragging?: boolean
}

export function SelectedQuestionRow({
  index,
  value,
  error,
  onChange,
  onRemove,
  onDragStart,
  onDragOver,
  onDrop,
  isDragging = false,
}: SelectedQuestionRowProps) {
  const { t } = useTranslation()
  const [previewOpen, setPreviewOpen] = useState(false)
  const [previewLoading, setPreviewLoading] = useState(false)
  const [previewData, setPreviewData] = useState<{
    title: string
    content: string
    subQuestions: Array<{
      content: string
      answerFormat: "image" | "text" | "code"
      language?: string | null
      score?: number
    }>
  } | null>(null)

  const isDeleted = value.questionDeleted === true
  const hasSubmissions = value.hasSubmissions === true
  const showDefaultHint = !value.pointsOverridden && !isDeleted

  useEffect(() => {
    if (!previewOpen || isDeleted) {
      return
    }

    const controller = new AbortController()

    void (async () => {
      setPreviewLoading(true)
      setPreviewData(null)
      try {
        const question = await getQuestionById(value.questionId, controller.signal)
        if (controller.signal.aborted) {
          return
        }
        if (question) {
          setPreviewData({
            title: question.title,
            content: question.content,
            subQuestions: question.subQuestions.map((item) => ({
              content: item.content,
              answerFormat: item.answerFormat,
              language: item.language,
              score: item.score,
            })),
          })
        } else {
          setPreviewData(null)
        }
      } catch {
        if (controller.signal.aborted) {
          return
        }
        setPreviewData(null)
      } finally {
        if (!controller.signal.aborted) {
          setPreviewLoading(false)
        }
      }
    })()

    return () => controller.abort()
  }, [previewOpen, value.questionId, isDeleted])

  return (
    <>
      <div
        className={cn(
          "flex flex-col gap-1 rounded-lg border bg-card px-4 py-3",
          isDragging && "opacity-60",
          (error || isDeleted) && "border-destructive"
        )}
        onDragOver={isDeleted ? undefined : onDragOver}
        onDrop={isDeleted ? undefined : onDrop}
        aria-invalid={Boolean(error || isDeleted)}
      >
        <div className="flex flex-wrap items-center gap-3">
          <button
            type="button"
            draggable={!isDeleted}
            disabled={isDeleted}
            onDragStart={isDeleted ? undefined : onDragStart}
            className={cn(
              "text-muted-foreground",
              isDeleted
                ? "cursor-not-allowed opacity-40"
                : "cursor-grab hover:text-foreground active:cursor-grabbing"
            )}
            aria-label={t("dragQuestionAria", { index: index + 1 })}
          >
            <GripVertical className="size-4" />
          </button>

          <div className="min-w-0 flex-1">
            <p
              className={cn(
                "truncate font-medium",
                isDeleted && "text-destructive"
              )}
            >
              {isDeleted ? t("assignments:form.questionDeleted") : value.title}
            </p>
          </div>

          <span className="text-sm text-muted-foreground">{t("assignments:form.subQuestionCount", { count: value.subQuestionCount })}</span>

          <div className="flex items-center gap-2">
            <Input
              type="number"
              min={1}
              value={value.points}
              disabled={isDeleted}
              onChange={(event) =>
                onChange({
                  ...value,
                  points: Number(event.target.value) || 0,
                  pointsOverridden: true,
                })
              }
              className="w-20"
              aria-label={t("scoreAria")}
              aria-invalid={Boolean(error)}
            />
            {showDefaultHint && (
              <span className="text-xs text-muted-foreground">{t("default")}</span>
            )}
            {value.pointsOverridden && !isDeleted && (
              <Button
                type="button"
                variant="link"
                size="sm"
                className="h-auto px-0 text-xs"
                onClick={() =>
                  onChange({
                    ...value,
                    points: value.questionTotalScore,
                    pointsOverridden: false,
                  })
                }
              >
                {t("restoreDefault")}
              </Button>
            )}
          </div>

          <div className="flex items-center gap-1">
            <Button
              type="button"
              variant="ghost"
              size="icon-sm"
              aria-label={t("previewAria")}
              disabled={isDeleted}
              onClick={() => setPreviewOpen(true)}
            >
              <Eye className="size-4" />
            </Button>
            {hasSubmissions ? (
              <Tooltip>
                <TooltipTrigger
                  render={
                    <span className="inline-flex">
                      <Button
                        type="button"
                        variant="ghost"
                        size="icon-sm"
                        aria-label={t("removeAria")}
                        disabled
                      >
                        <Trash2 className="size-4 text-destructive opacity-50" />
                      </Button>
                    </span>
                  }
                />
                <TooltipContent>{t("assignments:form.hasSubmissions")}</TooltipContent>
              </Tooltip>
            ) : (
              <Button
                type="button"
                variant="ghost"
                size="icon-sm"
                aria-label={t("removeAria")}
                onClick={onRemove}
              >
                <Trash2 className="size-4 text-destructive" />
              </Button>
            )}
          </div>
        </div>

        {isDeleted && (
          <p className="text-sm text-destructive pl-7">{t("assignments:form.deletedQuestion")}</p>
        )}

        {error && !isDeleted && (
          <p className="text-sm text-destructive pl-7">{error}</p>
        )}
      </div>

      <Dialog open={previewOpen} onOpenChange={setPreviewOpen}>
        <DialogContent className="max-h-[85vh] overflow-y-auto sm:max-w-3xl">
          <DialogHeader>
            <DialogTitle>{t("assignments:form.previewTitle")}</DialogTitle>
          </DialogHeader>
          {previewLoading ? (
            <p className="py-8 text-center text-muted-foreground">{t("loading")}</p>
          ) : previewData ? (
            <QuestionStudentView
              title={previewData.title}
              content={previewData.content}
              subQuestions={previewData.subQuestions}
            />
          ) : (
            <p className="py-8 text-center text-muted-foreground">{t("assignments:form.notFound")}</p>
          )}
        </DialogContent>
      </Dialog>
    </>
  )
}
