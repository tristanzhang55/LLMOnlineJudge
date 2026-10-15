import type { ApiFieldError } from "@/lib/api/client"

const QUESTION_FIELDS = new Set(["title", "content", "type", "difficulty", "tags"])
const SUB_QUESTION_FIELD_PATTERN = /^subQuestions\[(\d+)\]\.(\w+)$/
const ASSIGNMENT_FIELDS = new Set(["title", "description", "classId", "deadlineTime"])
const ASSIGNMENT_QUESTION_FIELD_PATTERN = /^questions\[(\d+)\]\.(\w+)$/

export function mapApiFieldErrors(fieldErrors: ApiFieldError[]): {
  questionErrors: Record<string, string>
  subQuestionErrors: Record<number, Record<string, string>>
  subQuestionsError?: string
  unmapped: string[]
} {
  const questionErrors: Record<string, string> = {}
  const subQuestionErrors: Record<number, Record<string, string>> = {}
  let subQuestionsError: string | undefined
  const unmapped: string[] = []

  for (const { field, message } of fieldErrors) {
    if (field === "subQuestions") {
      subQuestionsError = message
      continue
    }

    if (QUESTION_FIELDS.has(field)) {
      questionErrors[field] = message
      continue
    }

    const match = field.match(SUB_QUESTION_FIELD_PATTERN)
    if (match) {
      const index = Number(match[1])
      const subField = match[2]
      if (!subQuestionErrors[index]) {
        subQuestionErrors[index] = {}
      }
      subQuestionErrors[index][subField] = message
      continue
    }

    unmapped.push(`${field}: ${message}`)
  }

  return { questionErrors, subQuestionErrors, subQuestionsError, unmapped }
}

export function mapAssignmentFieldErrors(fieldErrors: ApiFieldError[]): {
  formErrors: Record<string, string>
  questionRowErrors: Record<number, string>
  questionsError?: string
  unmapped: string[]
} {
  const formErrors: Record<string, string> = {}
  const questionRowErrors: Record<number, string> = {}
  let questionsError: string | undefined
  const unmapped: string[] = []

  for (const { field, message } of fieldErrors) {
    if (field === "questions") {
      questionsError = message
      continue
    }

    if (ASSIGNMENT_FIELDS.has(field)) {
      formErrors[field] = message
      continue
    }

    const match = field.match(ASSIGNMENT_QUESTION_FIELD_PATTERN)
    if (match) {
      const index = Number(match[1])
      questionRowErrors[index] = message
      continue
    }

    unmapped.push(`${field}: ${message}`)
  }

  return { formErrors, questionRowErrors, questionsError, unmapped }
}
