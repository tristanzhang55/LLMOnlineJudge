import { apiFetch } from "@/lib/api/client"
import { PAGE_SIZE } from "@/lib/mock-assignments"

export type SubmissionRoundStatus =
  | "pending"
  | "grading_in_progress"
  | "completed"
  | "grading_failed"

export interface StudentSubmissionHistoryItem {
  experimentId: number
  experimentTitle: string
  submitTime: string
  questionCount: number
  status: SubmissionRoundStatus
  score: number | null
}

export interface PaginatedStudentSubmissionHistory {
  items: StudentSubmissionHistoryItem[]
  total: number
  page: number
  pageSize: number
  totalPages: number
}

interface ApiStudentSubmissionHistoryItem {
  experimentId: number
  experimentTitle: string | null
  submitTime: string
  questionCount: number
  status: string
  score: number | null
}

interface ApiStudentSubmissionHistoryResponse {
  data: ApiStudentSubmissionHistoryItem[]
  total: number
}

function normalizeStatus(value: string): SubmissionRoundStatus {
  if (
    value === "pending" ||
    value === "grading_in_progress" ||
    value === "completed" ||
    value === "grading_failed"
  ) {
    return value
  }
  return "pending"
}

function normalizeItem(item: ApiStudentSubmissionHistoryItem): StudentSubmissionHistoryItem {
  const status = normalizeStatus(item.status)
  return {
    experimentId: item.experimentId,
    experimentTitle: item.experimentTitle ?? "",
    submitTime: item.submitTime,
    questionCount: item.questionCount,
    status,
    score: status === "completed" ? item.score : null,
  }
}

export async function fetchSubmissionHistory(
  params: { page?: number; pageSize?: number },
  signal?: AbortSignal,
): Promise<PaginatedStudentSubmissionHistory> {
  const page = params.page ?? 1
  const pageSize = params.pageSize ?? PAGE_SIZE
  const search = new URLSearchParams({
    page: String(page),
    pageSize: String(pageSize),
  })

  const json = await apiFetch<ApiStudentSubmissionHistoryResponse>(
    `/student/submissions?${search}`,
    { signal },
  )
  const total = json.total ?? 0
  const totalPages = Math.max(1, Math.ceil(total / pageSize))

  return {
    items: (json.data ?? []).map(normalizeItem),
    total,
    page,
    pageSize,
    totalPages,
  }
}
