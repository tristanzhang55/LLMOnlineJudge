import { useState } from "react"
import { useLocation, useNavigate } from "react-router-dom"

import { QuestionForm } from "@/components/questions/QuestionForm"
import { createQuestion } from "@/lib/api/questions"
import {
  defaultQuestionFormValues,
  type QuestionFormPayload,
} from "@/lib/mock-questions"

interface CreateLocationState {
  draft?: QuestionFormPayload
}

export function QuestionCreate() {
  const navigate = useNavigate()
  const location = useLocation()
  const draft = (location.state as CreateLocationState | null)?.draft
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(values: QuestionFormPayload) {
    setSubmitting(true)
    try {
      await createQuestion(values)
      navigate("/teacher/questions")
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <QuestionForm
      initialValues={draft ?? defaultQuestionFormValues}
      onSubmit={handleSubmit}
      submitting={submitting}
    />
  )
}
