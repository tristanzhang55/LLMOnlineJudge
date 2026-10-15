import { apiFetch } from "@/lib/api/client"
import {
  DISPUTE_LIST_STATUS_BADGE_CLASS,
} from "@/lib/api/student-disputes"
import type { SubmissionRoundStatus } from "@/lib/api/teacher-submissions"
import { PAGE_SIZE } from "@/lib/mock-assignments"

export { DISPUTE_LIST_STATUS_BADGE_CLASS }

export interface TeacherDisputeListItem {
  id: number
  submissionId: number
  experimentId: number
  experimentTitle: string
  classId: number | null
  studentId: number
  studentName: string
  experimentQuestionId: number
  questionTitle: string
  submissionStatus: SubmissionRoundStatus
  score: number | null
  disputeStatus: 1 | 2
  disputeReason: string
  disputeTime: string
  manualComment: string | null
  resolveTime: string | null
  isManualOverride: boolean
}

export interface TeacherDisputeQuery {
  page?: number
  pageSize?: number
  studentName?: string
  classId?: number | null
  disputeStatus: 1 | 2
}

export interface PaginatedTeacherDisputes {
  items: TeacherDisputeListItem[]
  total: number
  page: number
  pageSize: number
  totalPages: number
}

interface ApiTeacherDisputeListItem {
  id: number
  submissionId: number
  experimentId: number
  experimentTitle?: string | null
  classId?: number | null
  studentId: number
  studentName?: string | null
  experimentQuestionId: number
  questionTitle?: string | null
  submissionStatus?: number | string | null
  score?: number | null
  disputeStatus?: number | null
  disputeReason?: string | null
  disputeTime?: string | null
  manualComment?: string | null
  resolveTime?: string | null
  isManualOverride?: number | boolean | null
}

interface ApiTeacherDisputeListResponse {
  data: ApiTeacherDisputeListItem[]
  total: number
}

function mapSubmissionStatus(value: number | string | null | undefined): SubmissionRoundStatus {
  const numeric = typeof value === "string" ? Number(value) : value
  if (numeric === 0) {
    return "pending"
  }
  if (numeric === 1) {
    return "grading_in_progress"
  }
  if (numeric === 2) {
    return "completed"
  }
  if (numeric === 3) {
    return "grading_failed"
  }
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

function normalizeListDisputeStatus(value: number | null | undefined): 1 | 2 {
  return value === 2 ? 2 : 1
}

function normalizeItem(item: ApiTeacherDisputeListItem): TeacherDisputeListItem {
  const submissionStatus = mapSubmissionStatus(item.submissionStatus)
  const classId =
    typeof item.classId === "number" && item.classId > 0 ? item.classId : null

  return {
    id: item.id,
    submissionId: item.submissionId,
    experimentId: item.experimentId,
    experimentTitle: item.experimentTitle ?? "",
    classId,
    studentId: item.studentId,
    studentName: item.studentName ?? "",
    experimentQuestionId: item.experimentQuestionId,
    questionTitle: item.questionTitle ?? "",
    submissionStatus,
    score: submissionStatus === "completed" ? (item.score ?? null) : null,
    disputeStatus: normalizeListDisputeStatus(item.disputeStatus),
    disputeReason: item.disputeReason ?? "",
    disputeTime: item.disputeTime ?? "",
    manualComment: item.manualComment?.trim() ? item.manualComment : null,
    resolveTime: item.resolveTime ?? null,
    isManualOverride: item.isManualOverride === 1 || item.isManualOverride === true,
  }
}

export function teacherDisputeDetailPath(item: TeacherDisputeListItem): string {
  const search = new URLSearchParams({
    submissionId: String(item.submissionId),
    questionId: String(item.experimentQuestionId),
  })
  return `/teacher/submissions/${item.experimentId}/students/${item.studentId}?${search}`
}

export function teacherDisputeDetailState(item: TeacherDisputeListItem): {
  experimentTitle: string
  questionTitle: string
  studentName: string
} {
  return {
    experimentTitle: item.experimentTitle,
    questionTitle: item.questionTitle,
    studentName: item.studentName,
  }
}

export const FORWARD_BATCH_MAX = 50

export interface ResolveDisputeBody {
  manualComment: string
  subQuestionScores: { subQuestionId: number; manualScore: number }[]
}

export interface TeacherDisputeWriteResult {
  id: number
  submissionId: number
  disputeStatus: 0 | 1 | 2 | 3
  disputeReason: string
  disputeTime: string
  manualComment: string | null
  resolveTime: string | null
  withdrawnTime: string | null
}

export type ForwardItemStatus =
  | "forwarded"
  | "already_resolved"
  | "already_withdrawn"
  | "not_found"
  | "conflict"

export interface ForwardDisputeResult {
  disputeId: number
  status: ForwardItemStatus
}

export function countForwardResults(results: ForwardDisputeResult[]): Record<ForwardItemStatus, number> {
  const counts: Record<ForwardItemStatus, number> = {
    forwarded: 0,
    already_resolved: 0,
    already_withdrawn: 0,
    not_found: 0,
    conflict: 0,
  }
  for (const item of results) {
    counts[item.status] += 1
  }
  return counts
}

interface ApiTeacherDisputeWriteResult {
  id: number
  submissionId: number
  disputeStatus?: number | null
  disputeReason?: string | null
  disputeTime?: string | null
  manualComment?: string | null
  resolveTime?: string | null
  withdrawnTime?: string | null
}

interface ApiForwardItem {
  disputeId?: number
  status?: string | null
}

interface ApiForwardResponse {
  results?: ApiForwardItem[] | null
}

function normalizeWriteDisputeStatus(value: number | null | undefined): 0 | 1 | 2 | 3 {
  if (value === 0 || value === 1 || value === 2 || value === 3) {
    return value
  }
  return 0
}

function blankToNull(value: string | null | undefined): string | null {
  const trimmed = value?.trim()
  return trimmed ? trimmed : null
}

function mapDisputeWriteResult(item: ApiTeacherDisputeWriteResult): TeacherDisputeWriteResult {
  return {
    id: item.id,
    submissionId: item.submissionId,
    disputeStatus: normalizeWriteDisputeStatus(item.disputeStatus),
    disputeReason: item.disputeReason?.trim() ? item.disputeReason : "",
    disputeTime: item.disputeTime ?? "",
    manualComment: blankToNull(item.manualComment),
    resolveTime: item.resolveTime ?? null,
    withdrawnTime: item.withdrawnTime ?? null,
  }
}

function normalizeForwardStatus(value: string | null | undefined): ForwardItemStatus {
  if (
    value === "forwarded" ||
    value === "already_resolved" ||
    value === "already_withdrawn" ||
    value === "not_found" ||
    value === "conflict"
  ) {
    return value
  }
  return "not_found"
}

export function uniquePositiveIds(ids: number[]): number[] {
  const seen = new Set<number>()
  const result: number[] = []
  for (const id of ids) {
    if (!Number.isSafeInteger(id) || id <= 0 || seen.has(id)) {
      continue
    }
    seen.add(id)
    result.push(id)
  }
  return result
}

export async function resolveTeacherDispute(
  disputeId: number,
  body: ResolveDisputeBody,
): Promise<TeacherDisputeWriteResult> {
  const item = await apiFetch<ApiTeacherDisputeWriteResult>(
    `/experiments/disputes/${disputeId}/resolve`,
    {
      method: "POST",
      body: JSON.stringify(body),
    },
  )
  return mapDisputeWriteResult(item)
}

export async function forwardTeacherDisputes(
  note: string,
  disputeIds: number[],
): Promise<ForwardDisputeResult[]> {
  const uniqueIds = uniquePositiveIds(disputeIds)
  const json = await apiFetch<ApiForwardResponse>("/experiments/disputes/forward", {
    method: "POST",
    body: JSON.stringify({ note, disputeIds: uniqueIds }),
  })
  return (json.results ?? []).map((item) => ({
    disputeId: typeof item.disputeId === "number" ? item.disputeId : 0,
    status: normalizeForwardStatus(item.status),
  }))
}

export async function fetchTeacherDisputes(
  params: TeacherDisputeQuery,
  signal?: AbortSignal,
): Promise<PaginatedTeacherDisputes> {
  const page = params.page ?? 1
  const pageSize = params.pageSize ?? PAGE_SIZE
  const search = new URLSearchParams({
    page: String(page),
    pageSize: String(pageSize),
    disputeStatus: String(params.disputeStatus),
  })

  if (params.studentName?.trim()) {
    search.set("studentName", params.studentName.trim())
  }
  if (params.classId != null) {
    search.set("classId", String(params.classId))
  }

  const json = await apiFetch<ApiTeacherDisputeListResponse>(`/experiments/disputes?${search}`, {
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
