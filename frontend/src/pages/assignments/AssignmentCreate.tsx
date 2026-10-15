import { useState } from "react"
import { useNavigate } from "react-router-dom"

import { AssignmentForm } from "@/components/assignments/AssignmentForm"
import { createExperiment } from "@/lib/api/experiments"
import type { AssignmentFormPayload } from "@/lib/mock-assignments"

export function AssignmentCreate() {
  const navigate = useNavigate()
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(values: AssignmentFormPayload) {
    setSubmitting(true)
    try {
      await createExperiment({ ...values, classId: values.classId! })
      navigate("/teacher/assignments")
    } finally {
      setSubmitting(false)
    }
  }

  return <AssignmentForm onSubmit={handleSubmit} submitting={submitting} />
}
