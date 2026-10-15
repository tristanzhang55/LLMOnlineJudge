import { useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import { Link, useLocation, useNavigate, useParams } from "react-router-dom"

import { QuestionForm } from "@/components/questions/QuestionForm"
import { Button, buttonVariants } from "@/components/ui/button"
import { getQuestionById, updateQuestion } from "@/lib/api/questions"
import {
  questionToFormPayload,
  type QuestionFormPayload,
} from "@/lib/mock-questions"
import { cn } from "@/lib/utils"

interface EditLocationState {
  draft?: QuestionFormPayload
}

function QuestionNotFound() {
  const { t } = useTranslation()
  return (
    <div className="flex flex-col items-center justify-center gap-4 py-16 text-center">
      <p className="text-muted-foreground">{t("questions:edit.notFound")}</p>
      <Link to="/teacher/questions" className={cn(buttonVariants({ variant: "outline" }))}>
        {t("questions:edit.backBank")}
      </Link>
    </div>
  )
}

function QuestionLoadError({ onRetry }: { onRetry: () => void }) {
  const { t } = useTranslation()
  return (
    <div className="flex flex-col items-center justify-center gap-4 py-16 text-center">
      <p className="text-muted-foreground">{t("questions:edit.loadFailed")}</p>
      <div className="flex gap-2">
        <Button type="button" onClick={onRetry}>
          {t("retry")}
        </Button>
        <Link to="/teacher/questions" className={cn(buttonVariants({ variant: "outline" }))}>
          {t("questions:edit.backBank")}
        </Link>
      </div>
    </div>
  )
}

export function QuestionEdit() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const location = useLocation()
  const previewDraft = (location.state as EditLocationState | null)?.draft
  const { qid } = useParams()
  const questionId = Number(qid)
  const isValidId = Number.isSafeInteger(questionId) && questionId > 0

  const [initialValues, setInitialValues] = useState<QuestionFormPayload | null>(null)
  const [loading, setLoading] = useState(() => isValidId && !previewDraft)
  const [notFound, setNotFound] = useState(false)
  const [loadError, setLoadError] = useState(false)
  const [retryKey, setRetryKey] = useState(0)
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    if (!isValidId || previewDraft) {
      return
    }

    const controller = new AbortController()

    void (async () => {
      await Promise.resolve()
      if (controller.signal.aborted) {
        return
      }

      setLoading(true)
      setLoadError(false)
      setNotFound(false)

      try {
        const question = await getQuestionById(questionId, controller.signal)
        if (controller.signal.aborted) {
          return
        }

        if (!question) {
          setNotFound(true)
          setInitialValues(null)
        } else {
          setInitialValues(questionToFormPayload(question))
          setNotFound(false)
        }
      } catch {
        if (controller.signal.aborted) {
          return
        }
        setLoadError(true)
        setInitialValues(null)
      } finally {
        if (!controller.signal.aborted) {
          setLoading(false)
        }
      }
    })()

    return () => controller.abort()
  }, [questionId, previewDraft, retryKey, isValidId])

  async function handleSubmit(values: QuestionFormPayload) {
    if (!isValidId) {
      return
    }

    setSubmitting(true)
    try {
      await updateQuestion(questionId, values)
      navigate("/teacher/questions")
    } finally {
      setSubmitting(false)
    }
  }

  if (!isValidId) {
    return <QuestionNotFound />
  }

  if (previewDraft) {
    return (
      <QuestionForm
        key={`${questionId}-preview-draft`}
        initialValues={previewDraft}
        onSubmit={handleSubmit}
        submitting={submitting}
      />
    )
  }

  if (loading) {
    return <div className="py-16 text-center text-muted-foreground">{t("loading")}</div>
  }

  if (loadError) {
    return <QuestionLoadError onRetry={() => setRetryKey((current) => current + 1)} />
  }

  if (notFound || !initialValues) {
    return <QuestionNotFound />
  }

  return (
    <QuestionForm
      key={questionId}
      initialValues={initialValues}
      onSubmit={handleSubmit}
      submitting={submitting}
    />
  )
}
