import { ApiError, apiFetch } from "@/lib/api/client"
import {
  PAGE_SIZE,
  type AnswerFormat,
  type PaginatedQuestions,
  type Question,
  type QuestionDifficulty,
  type QuestionFormPayload,
  type QuestionType,
  type SubQuestion,
} from "@/lib/mock-questions"

interface SubQuestionCreateRequest {
  content: string
  answerFormat: AnswerFormat
  language?: string
  referenceAnswer?: string
  rubric?: string
  allowSandbox: boolean
  score: number
}

interface QuestionCreateRequest {
  title: string
  content: string
  type: QuestionType
  difficulty: QuestionDifficulty
  tags?: string
  subQuestions: SubQuestionCreateRequest[]
}

interface SubQuestionUpdateRequest extends SubQuestionCreateRequest {
  id?: number
}

interface QuestionUpdateRequest {
  title: string
  content: string
  type: QuestionType
  difficulty: QuestionDifficulty
  tags?: string
  subQuestions: SubQuestionUpdateRequest[]
}

function mapSubQuestionBody(item: QuestionFormPayload["subQuestions"][number]) {
  const referenceAnswer = item.referenceAnswer?.trim()
  const rubric = item.rubric?.trim()
  const language = item.language?.trim()

  return {
    content: item.content.trim(),
    answerFormat: item.answerFormat,
    allowSandbox: item.allowSandbox,
    score: item.score,
    ...(item.answerFormat === "code" && language ? { language } : {}),
    ...(referenceAnswer ? { referenceAnswer } : {}),
    ...(rubric ? { rubric } : {}),
  }
}

function mapQuestionFields(payload: QuestionFormPayload) {
  const tags = payload.tags?.trim()

  return {
    title: payload.title.trim(),
    content: payload.content.trim(),
    type: payload.type,
    difficulty: payload.difficulty,
    ...(tags ? { tags } : {}),
  }
}

function toCreateRequest(payload: QuestionFormPayload): QuestionCreateRequest {
  return {
    ...mapQuestionFields(payload),
    subQuestions: payload.subQuestions.map(mapSubQuestionBody),
  }
}

function toUpdateRequest(payload: QuestionFormPayload): QuestionUpdateRequest {
  return {
    ...mapQuestionFields(payload),
    subQuestions: payload.subQuestions.map((item) => ({
      ...mapSubQuestionBody(item),
      ...(item.id != null ? { id: item.id } : {}),
    })),
  }
}

interface QuestionListResponse {
  data: ApiQuestion[]
  total: number
}

interface ApiSubQuestion {
  id: number
  questionId: number
  orderIndex: number
  content: string | null
  answerFormat: "image" | "text" | "code"
  language: string | null
  referenceAnswer: string | null
  rubric: string | null
  allowSandbox: boolean
  score: number
  createTime: string
  updateTime: string
  isDelete: boolean
}

interface ApiQuestion {
  id: number
  title: string | null
  content: string | null
  type: QuestionType | null
  difficulty: QuestionDifficulty | null
  tags: string | null
  totalScore: number
  submitNum: number
  userId: number
  createTime: string
  updateTime: string
  isDelete: boolean
  subQuestions: ApiSubQuestion[]
}

function toDeleteFlag(value: boolean): 0 | 1 {
  return value ? 1 : 0
}

function normalizeSubQuestion(item: ApiSubQuestion): SubQuestion {
  return {
    id: item.id,
    questionId: item.questionId,
    orderIndex: item.orderIndex,
    content: item.content ?? "",
    answerFormat: item.answerFormat,
    language: item.language ?? null,
    referenceAnswer: item.referenceAnswer ?? undefined,
    rubric: item.rubric ?? undefined,
    allowSandbox: item.allowSandbox,
    score: item.score,
    createTime: item.createTime,
    updateTime: item.updateTime,
    isDelete: toDeleteFlag(item.isDelete),
  }
}

function normalizeQuestion(item: ApiQuestion): Question {
  return {
    id: item.id,
    title: item.title ?? "",
    content: item.content ?? "",
    type: item.type ?? "shell_script",
    difficulty: item.difficulty ?? "easy",
    tags: item.tags ?? undefined,
    totalScore: item.totalScore,
    submitNum: item.submitNum,
    userId: item.userId,
    createTime: item.createTime,
    updateTime: item.updateTime,
    isDelete: toDeleteFlag(item.isDelete),
    subQuestions: (item.subQuestions ?? []).map(normalizeSubQuestion),
  }
}

export interface SandboxLanguageOption {
  code: string
}

export async function fetchSandboxLanguages(
  signal?: AbortSignal,
): Promise<SandboxLanguageOption[]> {
  const body = await apiFetch<SandboxLanguageOption[]>(
    "/questions/sandbox-languages",
    { signal },
  )
  return Array.isArray(body)
    ? body.filter((item) => typeof item?.code === "string" && item.code.trim())
    : []
}

export async function fetchQuestions(
  params: {
    page: number
    pageSize?: number
    title?: string
  },
  signal?: AbortSignal,
): Promise<PaginatedQuestions> {
  const pageSize = params.pageSize ?? PAGE_SIZE
  const search = new URLSearchParams({
    page: String(params.page),
    pageSize: String(pageSize),
  })
  if (params.title?.trim()) {
    search.set("title", params.title.trim())
  }

  const json = await apiFetch<QuestionListResponse>(`/questions?${search}`, { signal })
  const total = json.total
  const totalPages = Math.max(1, Math.ceil(total / pageSize))

  return {
    items: json.data.map(normalizeQuestion),
    total,
    page: params.page,
    pageSize,
    totalPages,
  }
}

export async function createQuestion(payload: QuestionFormPayload): Promise<Question> {
  const created = await apiFetch<ApiQuestion>("/questions", {
    method: "POST",
    body: JSON.stringify(toCreateRequest(payload)),
  })

  return normalizeQuestion(created)
}

export async function getQuestionById(
  id: number,
  signal?: AbortSignal,
): Promise<Question | null> {
  try {
    const question = await apiFetch<ApiQuestion>(`/questions/${id}`, { signal })
    return normalizeQuestion(question)
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) {
      return null
    }
    throw error
  }
}

export async function updateQuestion(
  id: number,
  payload: QuestionFormPayload,
): Promise<Question> {
  const updated = await apiFetch<ApiQuestion>(`/questions/${id}`, {
    method: "PUT",
    body: JSON.stringify(toUpdateRequest(payload)),
  })

  return normalizeQuestion(updated)
}

export async function softDeleteQuestion(id: number): Promise<void> {
  await apiFetch<void>(`/questions/${id}`, { method: "DELETE" })
}
