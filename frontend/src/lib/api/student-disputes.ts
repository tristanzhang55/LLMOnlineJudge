import { apiFetch } from "@/lib/api/client"
import {
  mapDisputeWriteResult,
  type FileDisputeResult,
} from "@/lib/api/student-experiments"
import { PAGE_SIZE } from "@/lib/mock-assignments"

export type StudentListDisputeStatus = 1 | 2 | 3

export const DISPUTE_LIST_STATUS_BADGE_CLASS: Record<StudentListDisputeStatus, string> = {
  1: "border-transparent bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-400",
  2: "border-transparent bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400",
  3: "border-transparent bg-muted text-muted-foreground",
}

export interface StudentDisputeListItem {
  id: number
  submissionId: number
  experimentId: number
  experimentTitle: string
  experimentQuestionId: number
  questionTitle: string
  disputeStatus: StudentListDisputeStatus
  disputeTime: string
  score: number | null
}

export interface PaginatedStudentDisputes {
  items: StudentDisputeListItem[]
  total: number
  page: number
  pageSize: number
  totalPages: number
}

interface ApiStudentDisputeListItem {
  id: number
  submissionId: number
  experimentId: number
  experimentTitle?: string | null
  experimentQuestionId: number
  questionTitle?: string | null
  disputeStatus?: number | null
  disputeTime?: string | null
  score?: number | null
}

interface ApiStudentDisputeListResponse {
  data: ApiStudentDisputeListItem[]
  total: number
}

interface ApiDisputeWriteResult {
  id: number
  submissionId: number
  disputeStatus?: number | null
  disputeReason?: string | null
  disputeTime?: string | null
  manualComment?: string | null
  resolveTime?: string | null
  withdrawnTime?: string | null
}

function normalizeListDisputeStatus(value: number | null | undefined): StudentListDisputeStatus {
  if (value === 3) {
    return 3
  }
  if (value === 2) {
    return 2
  }
  return 1
}

function normalizeItem(item: ApiStudentDisputeListItem): StudentDisputeListItem {
  return {
    id: item.id,
    submissionId: item.submissionId,
    experimentId: item.experimentId,
    experimentTitle: item.experimentTitle ?? "",
    experimentQuestionId: item.experimentQuestionId,
    questionTitle: item.questionTitle ?? "",
    disputeStatus: normalizeListDisputeStatus(item.disputeStatus),
    disputeTime: item.disputeTime ?? "",
    score: item.score ?? null,
  }
}

export function studentDisputeDetailPath(item: StudentDisputeListItem): string {
  const search = new URLSearchParams({
    submissionId: String(item.submissionId),
    questionId: String(item.experimentQuestionId),
    disputeId: String(item.id),
  })
  return `/student/assignments/${item.experimentId}?${search}`
}

export async function fetchStudentDisputes(
  params: { page?: number; pageSize?: number },
  signal?: AbortSignal,
): Promise<PaginatedStudentDisputes> {
  const page = params.page ?? 1
  const pageSize = params.pageSize ?? PAGE_SIZE
  const search = new URLSearchParams({
    page: String(page),
    pageSize: String(pageSize),
  })

  const json = await apiFetch<ApiStudentDisputeListResponse>(`/student/disputes?${search}`, {
    signal,
  })
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

export async function withdrawStudentDispute(disputeId: number): Promise<FileDisputeResult> {
  const item = await apiFetch<ApiDisputeWriteResult>(`/student/disputes/${disputeId}/withdraw`, {
    method: "POST",
  })
  return mapDisputeWriteResult(item)
}
