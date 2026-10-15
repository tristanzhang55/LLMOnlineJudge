import { apiFetch, ApiError } from "@/lib/api/client"
import {
  PAGE_SIZE,
  type AssignmentFormPayload,
  type ExperimentStatus,
  type SelectedQuestionFormValue,
} from "@/lib/mock-assignments"

interface ApiExperimentQuestion {
  id: number
  experimentId: number
  questionId: number
  questionTitle: string | null
  questionTotalScore: number | null
  subQuestionCount: number
  points: number
  effectivePoints: number
  sortOrder: number
  questionDeleted?: boolean
  hasSubmissions?: boolean
  createTime: string
  updateTime: string
  isDelete: boolean
}

interface ApiExperiment {
  id: number
  title: string | null
  description: string | null
  classId: number | null
  status: number
  userId: number
  createTime: string
  updateTime: string
  deadlineTime: string | null
  lastPublishedTime?: string | null
  submittedStudentCount?: number | null
  isDelete: boolean
  questions?: ApiExperimentQuestion[]
}

interface ExperimentListResponse {
  data: ApiExperiment[]
  total: number
}

export interface ExperimentListItem {
  id: number
  title: string
  description: string | null
  classId: number | null
  status: ExperimentStatus
  userId: number
  createTime: string
  updateTime: string
  deadlineTime: string | null
  lastPublishedTime: string | null
  /** null = the list endpoint does not report it (006-2 §2.2.6) */
  questionCount: number | null
}

export interface ExperimentQuery {
  page?: number
  pageSize?: number
  title?: string
  classId?: number | null
  status?: ExperimentStatus | null
}

export interface PaginatedExperimentList {
  items: ExperimentListItem[]
  total: number
  page: number
  pageSize: number
  totalPages: number
}

export interface ExperimentQuestionItem {
  id: number
  experimentId: number
  questionId: number
  questionTitle: string
  questionTotalScore: number
  subQuestionCount: number
  points: number
  effectivePoints: number
  sortOrder: number
  questionDeleted: boolean
  hasSubmissions: boolean
}

export interface ExperimentDetail extends ExperimentListItem {
  submittedStudentCount: number
  questions: ExperimentQuestionItem[]
}

export interface CreateExperimentPayload {
  title: string
  description: string
  classId: number
  deadlineTime: string
  questions: SelectedQuestionFormValue[]
}

export type UpdateExperimentPayload = CreateExperimentPayload

function normalizeStatus(value: number): ExperimentStatus {
  if (value >= 0 && value <= 2) {
    return value as ExperimentStatus
  }
  return 0
}

function normalizeExperimentQuestion(item: ApiExperimentQuestion): ExperimentQuestionItem {
  return {
    id: item.id,
    experimentId: item.experimentId,
    questionId: item.questionId,
    questionTitle: item.questionTitle ?? "",
    questionTotalScore: item.questionTotalScore ?? 0,
    subQuestionCount: item.subQuestionCount,
    points: item.points,
    effectivePoints: item.effectivePoints,
    sortOrder: item.sortOrder,
    questionDeleted: item.questionDeleted ?? false,
    hasSubmissions: item.hasSubmissions ?? false,
  }
}

function normalizeExperiment(item: ApiExperiment): ExperimentListItem {
  return {
    id: item.id,
    title: item.title ?? "",
    description: item.description,
    classId: item.classId,
    status: normalizeStatus(item.status),
    userId: item.userId,
    createTime: item.createTime,
    updateTime: item.updateTime,
    deadlineTime: item.deadlineTime,
    lastPublishedTime: item.lastPublishedTime ?? null,
    questionCount: item.questions?.length ?? null,
  }
}

function toExperimentDetail(item: ApiExperiment): ExperimentDetail {
  return {
    ...normalizeExperiment(item),
    submittedStudentCount: item.submittedStudentCount ?? 0,
    questions: (item.questions ?? []).map(normalizeExperimentQuestion),
  }
}

function toExperimentRequestBody(payload: CreateExperimentPayload) {
  return {
    title: payload.title.trim(),
    ...(payload.description.trim() ? { description: payload.description.trim() } : {}),
    classId: payload.classId,
    deadlineTime: payload.deadlineTime,
    questions: payload.questions.map((row) => ({
      questionId: row.questionId,
      points: row.pointsOverridden ? row.points : 0,
    })),
  }
}

export function experimentToFormPayload(experiment: ExperimentDetail): AssignmentFormPayload {
  return {
    title: experiment.title,
    description: experiment.description ?? "",
    classId: experiment.classId,
    deadlineTime: experiment.deadlineTime ?? "",
    submittedStudentCount: experiment.submittedStudentCount,
    questions: experiment.questions.map((item) => ({
      questionId: item.questionId,
      title: item.questionDeleted ? "" : item.questionTitle,
      subQuestionCount: item.subQuestionCount,
      questionTotalScore: item.questionTotalScore,
      points: item.effectivePoints,
      pointsOverridden: item.points !== 0,
      sortOrder: item.sortOrder,
      questionDeleted: item.questionDeleted,
      hasSubmissions: item.hasSubmissions,
    })),
  }
}

export async function createExperiment(
  payload: CreateExperimentPayload,
): Promise<ExperimentDetail> {
  const item = await apiFetch<ApiExperiment>("/experiments", {
    method: "POST",
    body: JSON.stringify(toExperimentRequestBody(payload)),
  })

  return toExperimentDetail(item)
}

export async function getExperimentById(
  id: number,
  signal?: AbortSignal,
): Promise<ExperimentDetail | null> {
  try {
    const item = await apiFetch<ApiExperiment>(`/experiments/${id}`, { signal })
    return toExperimentDetail(item)
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) {
      return null
    }
    throw error
  }
}

export async function updateExperiment(
  id: number,
  payload: UpdateExperimentPayload,
): Promise<ExperimentDetail> {
  const item = await apiFetch<ApiExperiment>(`/experiments/${id}`, {
    method: "PUT",
    body: JSON.stringify(toExperimentRequestBody(payload)),
  })

  return toExperimentDetail(item)
}

export async function deleteExperiment(id: number): Promise<void> {
  await apiFetch<void>(`/experiments/${id}`, { method: "DELETE" })
}

export async function updateExperimentStatus(
  id: number,
  status: 0 | 1,
): Promise<ExperimentDetail> {
  const item = await apiFetch<ApiExperiment>(`/experiments/${id}/status`, {
    method: "PATCH",
    body: JSON.stringify({ status }),
  })

  return toExperimentDetail(item)
}

export async function fetchExperiments(
  params: ExperimentQuery,
  signal?: AbortSignal,
): Promise<PaginatedExperimentList> {
  const page = params.page ?? 1
  const pageSize = params.pageSize ?? PAGE_SIZE
  const search = new URLSearchParams({
    page: String(page),
    pageSize: String(pageSize),
  })

  if (params.title?.trim()) {
    search.set("title", params.title.trim())
  }
  if (params.classId != null) {
    search.set("classId", String(params.classId))
  }
  if (params.status != null) {
    search.set("status", String(params.status))
  }

  const json = await apiFetch<ExperimentListResponse>(`/experiments?${search}`, { signal })
  const total = json.total ?? 0
  const totalPages = Math.max(1, Math.ceil(total / pageSize))

  return {
    items: (json.data ?? []).map(normalizeExperiment),
    total,
    page,
    pageSize,
    totalPages,
  }
}
