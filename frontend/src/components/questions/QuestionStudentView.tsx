import { useTranslation } from "react-i18next"

import { MarkdownPreview } from "@/components/questions/MarkdownPreview"
import { Button } from "@/components/ui/button"
import { Label } from "@/components/ui/label"
import { Textarea } from "@/components/ui/textarea"
import {
  sandboxLanguageLabel,
  type AnswerFormat,
} from "@/lib/mock-questions"

export interface QuestionStudentViewSubQuestion {
  content: string
  answerFormat: AnswerFormat
  language?: string | null
  score?: number
}

export interface QuestionStudentViewProps {
  title: string
  content: string
  subQuestions: QuestionStudentViewSubQuestion[]
}

function DisabledAnswerInput({ answerFormat }: { answerFormat: AnswerFormat }) {
  const { t } = useTranslation()

  if (answerFormat === "image") {
    return (
      <div className="space-y-2">
        <input type="file" accept="image/*" disabled className="hidden" aria-hidden />
        <Button type="button" variant="outline" size="sm" disabled>
          {t("uploadImage")}
        </Button>
        <p className="text-xs text-muted-foreground">{t("questions:studentView.previewMode")}</p>
      </div>
    )
  }

  if (answerFormat === "code") {
    return (
      <Textarea
        disabled
        readOnly
        placeholder={t("questions:studentView.codePlaceholder")}
        className="min-h-28 font-mono text-sm"
      />
    )
  }

  return (
    <Textarea
      disabled
      readOnly
      placeholder={t("questions:studentView.textPlaceholder")}
      className="min-h-28"
    />
  )
}

export function QuestionStudentView({
  title,
  content,
  subQuestions,
}: QuestionStudentViewProps) {
  const { t } = useTranslation()

  return (
    <div className="mx-auto max-w-3xl space-y-8">
      <header className="space-y-2">
        <h1 className="text-2xl font-semibold tracking-tight">
          {title.trim() || t("emptyTitle")}
        </h1>
      </header>

      <section className="space-y-2">
        <Label>{t("questions:studentView.content")}</Label>
        <MarkdownPreview content={content} />
      </section>

      <section className="space-y-6">
        {subQuestions.length === 0 ? (
          <p className="text-sm text-muted-foreground">{t("questions:studentView.noSubQuestions")}</p>
        ) : (
          subQuestions.map((subQuestion, index) => (
            <div
              key={index}
              className="space-y-3 rounded-xl border bg-card px-4 py-4"
            >
              <div className="space-y-1">
                <div className="flex flex-wrap items-center gap-2">
                  <h2 className="font-medium">{t("questions:form.subQuestion", { index: index + 1 })}</h2>
                  <span className="text-xs text-muted-foreground">
                    {t(`questions:answerFormat.${subQuestion.answerFormat}`)}
                    {subQuestion.answerFormat === "code" && subQuestion.language?.trim()
                      ? ` · ${sandboxLanguageLabel(subQuestion.language.trim())}`
                      : ""}
                    {typeof subQuestion.score === "number"
                      ? ` · ${t("points", { count: subQuestion.score })}`
                      : ""}
                  </span>
                </div>
                {subQuestion.content.trim() ? (
                  <MarkdownPreview
                    content={subQuestion.content}
                    className="border-0 bg-transparent px-0 py-0"
                  />
                ) : (
                  <p className="text-sm text-muted-foreground">{t("questions:studentView.noContent")}</p>
                )}
              </div>

              <div className="space-y-2">
                <Label>{t("questions:studentView.yourAnswer")}</Label>
                <DisabledAnswerInput answerFormat={subQuestion.answerFormat} />
              </div>
            </div>
          ))
        )}
      </section>
    </div>
  )
}
