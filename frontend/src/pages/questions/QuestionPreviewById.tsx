import { useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import { Link, useNavigate, useParams } from "react-router-dom"

import { QuestionStudentView } from "@/components/questions/QuestionStudentView"
import { Button, buttonVariants } from "@/components/ui/button"
import { getQuestionById } from "@/lib/api/questions"
import type { Question } from "@/lib/mock-questions"
import { cn } from "@/lib/utils"

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

export function QuestionPreviewById() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { qid } = useParams()
  const questionId = Number(qid)
  const isValidId = Number.isSafeInteger(questionId) && questionId > 0

  const [question, setQuestion] = useState<Question | null>(null)
  const [loading, setLoading] = useState(isValidId)
  const [notFound, setNotFound] = useState(false)
  const [loadError, setLoadError] = useState(false)
  const [retryKey, setRetryKey] = useState(0)

  useEffect(() => {
    if (!isValidId) {
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
        const result = await getQuestionById(questionId, controller.signal)
        if (controller.signal.aborted) {
          return
        }

        if (!result) {
          setNotFound(true)
          setQuestion(null)
        } else {
          setQuestion(result)
          setNotFound(false)
        }
      } catch {
        if (controller.signal.aborted) {
          return
        }
        setLoadError(true)
        setQuestion(null)
      } finally {
        if (!controller.signal.aborted) {
          setLoading(false)
        }
      }
    })()

    return () => controller.abort()
  }, [questionId, retryKey, isValidId])

  if (!isValidId) {
    return <QuestionNotFound />
  }

  if (loading) {
    return <div className="py-16 text-center text-muted-foreground">{t("loading")}</div>
  }

  if (loadError) {
    return <QuestionLoadError onRetry={() => setRetryKey((current) => current + 1)} />
  }

  if (notFound || !question) {
    return <QuestionNotFound />
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between gap-4">
        <p className="text-sm text-muted-foreground">{t("questions:preview.student")}</p>
        <Button type="button" variant="outline" onClick={() => navigate(-1)}>
          {t("back")}
        </Button>
      </div>
      <QuestionStudentView
        title={question.title}
        content={question.content}
        subQuestions={question.subQuestions}
      />
    </div>
  )
}
