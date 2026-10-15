import { useEffect, useRef, useState } from "react"
import type { TFunction } from "i18next"
import { useTranslation } from "react-i18next"
import { useLocation, useNavigate } from "react-router-dom"

import { SubQuestionCard } from "@/components/questions/SubQuestionCard"
import { MarkdownPreview } from "@/components/questions/MarkdownPreview"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { Textarea } from "@/components/ui/textarea"
import { IMAGE_ACCEPT, useMarkdownImageUpload } from "@/hooks/useMarkdownImageUpload"
import { ApiError } from "@/lib/api/client"
import { mapApiFieldErrors } from "@/lib/api/field-errors"
import { fetchSandboxLanguages, type SandboxLanguageOption } from "@/lib/api/questions"
import {
  computeFormTotalScore,
  createEmptySubQuestion,
  defaultQuestionFormValues,
  QUESTION_DIFFICULTIES,
  QUESTION_TYPES,
  type QuestionFormPayload,
  type SubQuestionFormValue,
} from "@/lib/mock-questions"

interface QuestionFormProps {
  initialValues?: QuestionFormPayload
  onSubmit: (values: QuestionFormPayload) => Promise<void>
  submitting?: boolean
}

type QuestionFieldErrors = Partial<Record<keyof QuestionFormPayload, string>>
type SubQuestionErrors = Partial<Record<number, Partial<Record<keyof SubQuestionFormValue, string>>>>

const MAX_TITLE_LENGTH = 512
const MAX_TAGS_LENGTH = 1024
const MAX_SUB_QUESTIONS = 50
const MAX_SCORE = 1000

function validateForm(
  values: QuestionFormPayload,
  t: TFunction,
): {
  questionErrors: QuestionFieldErrors
  subQuestionErrors: SubQuestionErrors
  subQuestionsError?: string
} {
  const questionErrors: QuestionFieldErrors = {}
  const subQuestionErrors: SubQuestionErrors = {}

  if (!values.title.trim()) {
    questionErrors.title = t("questions:form.titleRequired")
  } else if (values.title.trim().length > MAX_TITLE_LENGTH) {
    questionErrors.title = t("questions:form.titleTooLong")
  }
  if (!values.content.trim()) {
    questionErrors.content = t("questions:form.contentRequired")
  }
  if (values.tags && values.tags.trim().length > MAX_TAGS_LENGTH) {
    questionErrors.tags = t("questions:form.tagsTooLong")
  }

  if (values.subQuestions.length === 0) {
    return {
      questionErrors,
      subQuestionErrors,
      subQuestionsError: t("questions:form.minSubQuestions"),
    }
  }

  if (values.subQuestions.length > MAX_SUB_QUESTIONS) {
    return {
      questionErrors,
      subQuestionErrors,
      subQuestionsError: t("questions:form.maxSubQuestions"),
    }
  }

  values.subQuestions.forEach((subQuestion, index) => {
    const itemErrors: Partial<Record<keyof SubQuestionFormValue, string>> = {}
    if (!subQuestion.content.trim()) {
      itemErrors.content = t("questions:form.subContentRequired")
    }
    if (!subQuestion.score || subQuestion.score <= 0) {
      itemErrors.score = t("questions:form.scoreRequired")
    } else if (subQuestion.score > MAX_SCORE) {
      itemErrors.score = t("questions:form.scoreTooHigh")
    }
    if (subQuestion.answerFormat === "code" && !subQuestion.language?.trim()) {
      itemErrors.language = t("questions:form.languageRequired")
    }
    if (Object.keys(itemErrors).length > 0) {
      subQuestionErrors[index] = itemErrors
    }
  })

  return { questionErrors, subQuestionErrors }
}

export function QuestionForm({
  initialValues = defaultQuestionFormValues,
  onSubmit,
  submitting = false,
}: QuestionFormProps) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const location = useLocation()
  const contentRef = useRef<HTMLTextAreaElement>(null)
  const dragIndexRef = useRef<number | null>(null)

  const [values, setValues] = useState<QuestionFormPayload>(initialValues)
  const [questionErrors, setQuestionErrors] = useState<QuestionFieldErrors>({})
  const [subQuestionErrors, setSubQuestionErrors] = useState<SubQuestionErrors>({})
  const [subQuestionsError, setSubQuestionsError] = useState<string>()
  const [submitError, setSubmitError] = useState<string>()
  const [draggingIndex, setDraggingIndex] = useState<number | null>(null)
  const [sandboxLanguages, setSandboxLanguages] = useState<SandboxLanguageOption[]>([])
  const [languagesLoading, setLanguagesLoading] = useState(true)
  const [languagesError, setLanguagesError] = useState(false)
  const [languagesReloadKey, setLanguagesReloadKey] = useState(0)

  useEffect(() => {
    const controller = new AbortController()

    void (async () => {
      setLanguagesLoading(true)
      setLanguagesError(false)
      try {
        const languages = await fetchSandboxLanguages(controller.signal)
        if (controller.signal.aborted) {
          return
        }
        setSandboxLanguages(languages)
      } catch (error) {
        if (
          controller.signal.aborted ||
          (error instanceof DOMException && error.name === "AbortError")
        ) {
          return
        }
        setLanguagesError(true)
        setSandboxLanguages([])
      } finally {
        if (!controller.signal.aborted) {
          setLanguagesLoading(false)
        }
      }
    })()

    return () => controller.abort()
  }, [languagesReloadKey])

  const {
    fileInputRef,
    uploading: uploadingImage,
    error: imageUploadError,
    insertImage,
  } = useMarkdownImageUpload({
    ownerType: "question",
    textareaRef: contentRef,
    getValue: () => values.content,
    onChange: (next) => {
      setValues((current) => ({ ...current, content: next }))
      setQuestionErrors((current) => ({ ...current, content: undefined }))
    },
  })

  const totalScore = computeFormTotalScore(values.subQuestions)

  function updateQuestionField<K extends keyof QuestionFormPayload>(
    field: K,
    fieldValue: QuestionFormPayload[K]
  ) {
    setValues((current) => ({ ...current, [field]: fieldValue }))
    setQuestionErrors((current) => ({ ...current, [field]: undefined }))
  }

  function updateSubQuestion(index: number, nextValue: SubQuestionFormValue) {
    setValues((current) => ({
      ...current,
      subQuestions: current.subQuestions.map((item, itemIndex) =>
        itemIndex === index ? { ...nextValue, orderIndex: itemIndex } : item
      ),
    }))
    setSubQuestionErrors((current) => ({ ...current, [index]: undefined }))
    setSubQuestionsError(undefined)
  }

  function addSubQuestion() {
    if (values.subQuestions.length >= MAX_SUB_QUESTIONS) {
      return
    }

    setValues((current) => ({
      ...current,
      subQuestions: [...current.subQuestions, createEmptySubQuestion(current.subQuestions.length)],
    }))
    setSubQuestionsError(undefined)
  }

  function removeSubQuestion(index: number) {
    setValues((current) => ({
      ...current,
      subQuestions: current.subQuestions
        .filter((_, itemIndex) => itemIndex !== index)
        .map((item, itemIndex) => ({ ...item, orderIndex: itemIndex })),
    }))
    setSubQuestionErrors({})
  }

  function reorderSubQuestions(fromIndex: number, toIndex: number) {
    if (fromIndex === toIndex) {
      return
    }

    setValues((current) => {
      const next = [...current.subQuestions]
      const [moved] = next.splice(fromIndex, 1)
      next.splice(toIndex, 0, moved)
      return {
        ...current,
        subQuestions: next.map((item, index) => ({ ...item, orderIndex: index })),
      }
    })
  }

  function applySubmitError(error: unknown) {
    if (error instanceof ApiError) {
      if (error.fieldErrors && error.fieldErrors.length > 0) {
        const mapped = mapApiFieldErrors(error.fieldErrors)
        setQuestionErrors(mapped.questionErrors as QuestionFieldErrors)
        setSubQuestionErrors(mapped.subQuestionErrors)
        setSubQuestionsError(mapped.subQuestionsError)
        if (mapped.unmapped.length > 0) {
          setSubmitError(mapped.unmapped.join(t("listSep")))
        }
        return
      }
      setSubmitError(error.message)
      return
    }

    setSubmitError(t("questions:form.saveFailed"))
  }

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault()

    setSubmitError(undefined)
    setQuestionErrors({})
    setSubQuestionErrors({})
    setSubQuestionsError(undefined)

    const validation = validateForm(values, t)
    setQuestionErrors(validation.questionErrors)
    setSubQuestionErrors(validation.subQuestionErrors)
    setSubQuestionsError(validation.subQuestionsError)

    if (
      Object.keys(validation.questionErrors).length > 0 ||
      validation.subQuestionsError ||
      Object.keys(validation.subQuestionErrors).length > 0
    ) {
      return
    }

    try {
      await onSubmit({
        ...values,
        title: values.title.trim(),
        content: values.content.trim(),
        tags: values.tags?.trim() || "",
        subQuestions: values.subQuestions.map((item, index) => ({
          ...item,
          orderIndex: index,
          content: item.content.trim(),
          referenceAnswer: item.referenceAnswer?.trim() || "",
          rubric: item.rubric?.trim() || "",
        })),
      })
    } catch (error) {
      applySubmitError(error)
    }
  }

  return (
    <form onSubmit={(event) => void handleSubmit(event)} className="mx-auto max-w-4xl space-y-8">
      <section className="space-y-6">
        <h2 className="text-lg font-semibold">{t("questions:form.infoHeading")}</h2>

        <div className="space-y-2">
          <Label htmlFor="title">{t("questions:form.title")}</Label>
          <Input
            id="title"
            value={values.title}
            onChange={(event) => updateQuestionField("title", event.target.value)}
            aria-invalid={Boolean(questionErrors.title)}
          />
          {questionErrors.title && (
            <p className="text-sm text-destructive">{questionErrors.title}</p>
          )}
        </div>

        <div className="space-y-2">
          <div className="flex items-center justify-between gap-2">
            <Label htmlFor="content">{t("questions:form.content")}</Label>
            <div className="flex flex-col items-end gap-1">
              <div className="flex items-center gap-2">
                <input
                  ref={fileInputRef}
                  type="file"
                  accept={IMAGE_ACCEPT}
                  className="hidden"
                  onChange={(event) => {
                    const file = event.target.files?.[0]
                    if (file) {
                      void insertImage(file)
                    }
                  }}
                />
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  disabled={uploadingImage}
                  onClick={() => fileInputRef.current?.click()}
                >
                  {uploadingImage ? t("uploading") : t("insertImage")}
                </Button>
              </div>
              {imageUploadError && (
                <p className="text-sm text-destructive">{imageUploadError}</p>
              )}
            </div>
          </div>
          <Textarea
            ref={contentRef}
            id="content"
            value={values.content}
            onChange={(event) => updateQuestionField("content", event.target.value)}
            className="min-h-40 font-mono text-sm"
            aria-invalid={Boolean(questionErrors.content)}
          />
          {questionErrors.content && (
            <p className="text-sm text-destructive">{questionErrors.content}</p>
          )}
          <div className="space-y-2">
            <Label>{t("questions:form.preview")}</Label>
            <MarkdownPreview content={values.content} />
          </div>
        </div>

        <div className="grid gap-6 md:grid-cols-2">
          <div className="space-y-2">
            <Label>{t("questions:form.type")}</Label>
            <Select
              value={values.type}
              onValueChange={(nextValue) =>
                updateQuestionField("type", nextValue as QuestionFormPayload["type"])
              }
            >
              <SelectTrigger className="w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {QUESTION_TYPES.map((key) => (
                  <SelectItem key={key} value={key}>
                    {t(`questions:type.${key}`)}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <div className="space-y-2">
            <Label>{t("questions:form.difficulty")}</Label>
            <Select
              value={values.difficulty}
              onValueChange={(nextValue) =>
                updateQuestionField(
                  "difficulty",
                  nextValue as QuestionFormPayload["difficulty"]
                )
              }
            >
              <SelectTrigger className="w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {QUESTION_DIFFICULTIES.map((key) => (
                  <SelectItem key={key} value={key}>
                    {t(`questions:difficulty.${key}`)}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        </div>

        <div className="space-y-2">
          <Label htmlFor="tags">{t("questions:form.tags")}</Label>
          <Input
            id="tags"
            value={values.tags ?? ""}
            onChange={(event) => updateQuestionField("tags", event.target.value)}
            placeholder={t("questions:form.tagsPlaceholder")}
            aria-invalid={Boolean(questionErrors.tags)}
          />
          {questionErrors.tags && (
            <p className="text-sm text-destructive">{questionErrors.tags}</p>
          )}
        </div>

        <div className="space-y-2">
          <Label>{t("questions:form.totalScore")}</Label>
          <Input value={totalScore} readOnly disabled />
        </div>
      </section>

      <section className="space-y-4">
        <div className="flex items-center justify-between gap-4">
          <h2 className="text-lg font-semibold">{t("questions:form.subQuestions")}</h2>
          <Button
            type="button"
            variant="outline"
            onClick={addSubQuestion}
            disabled={values.subQuestions.length >= MAX_SUB_QUESTIONS}
          >
            {t("questions:form.addSubQuestion")}
          </Button>
        </div>

        {subQuestionsError && (
          <p className="text-sm text-destructive">{subQuestionsError}</p>
        )}

        {languagesError && (
          <div className="flex flex-wrap items-center gap-3">
            <p className="text-sm text-destructive">{t("questions:form.languagesFailed")}</p>
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => setLanguagesReloadKey((current) => current + 1)}
            >
              {t("retry")}
            </Button>
          </div>
        )}

        <div className="space-y-4">
          {values.subQuestions.map((subQuestion, index) => (
            <SubQuestionCard
              key={`${subQuestion.id ?? "new"}-${index}`}
              index={index}
              value={subQuestion}
              onChange={(nextValue) => updateSubQuestion(index, nextValue)}
              onDelete={() => removeSubQuestion(index)}
              onDragStart={() => {
                dragIndexRef.current = index
                setDraggingIndex(index)
              }}
              onDragOver={(event) => event.preventDefault()}
              onDrop={() => {
                if (dragIndexRef.current !== null) {
                  reorderSubQuestions(dragIndexRef.current, index)
                }
                dragIndexRef.current = null
                setDraggingIndex(null)
              }}
              isDragging={draggingIndex === index}
              errors={subQuestionErrors[index]}
              languages={sandboxLanguages}
              languagesLoading={languagesLoading}
            />
          ))}
        </div>
      </section>

      <div className="flex flex-wrap items-center justify-between gap-3 border-t pt-6">
        <Button
          type="button"
          variant="secondary"
          onClick={() =>
            navigate("/teacher/questions/preview", {
              state: { draft: values, returnTo: location.pathname },
            })
          }
          disabled={submitting}
        >
          {t("questions:form.previewAction")}
        </Button>
        <div className="flex flex-col items-end gap-2">
          {submitError && (
            <p className="text-sm text-destructive">{submitError}</p>
          )}
          <div className="flex gap-2">
            <Button
              type="button"
              variant="outline"
              onClick={() => navigate("/teacher/questions")}
              disabled={submitting}
            >
              {t("cancel")}
            </Button>
            <Button type="submit" disabled={submitting || uploadingImage}>
              {submitting ? t("saving") : t("save")}
            </Button>
          </div>
        </div>
      </div>
    </form>
  )
}
