import { apiFetch } from "@/lib/api/client"
import { getExperimentById } from "@/lib/api/experiments"
import { getQuestionById } from "@/lib/api/questions"
import { PAGE_SIZE } from "@/lib/mock-assignments"
import type { SubmissionRoundStatus } from "@/lib/api/student-submissions"
import type {
  AnswerBlockItem,
  SubQuestionAnswerBlocks,
} from "@/lib/api/student-experiments"

export type { SubmissionRoundStatus }

export const ROUND_STATUS_BADGE_CLASS: Record<SubmissionRoundStatus, string> = {
  pending:
    "border-border bg-background text-muted-foreground dark:border-input dark:bg-input/30",
  grading_in_progress:
    "border-transparent bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-400",
  completed:
    "border-transparent bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400",
  grading_failed:
    "border-transparent bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-400",
}

export type AnswerFormat = "text" | "image" | "code"
export type QuestionStatus = 0 | 1 | 2 | 3

export interface TeacherSubmissionListItem {
  experimentId: number
  experimentTitle: string
  classId: number | null
  studentId: number
  studentName: string
  submitTime: string
  questionCount: number
  status: SubmissionRoundStatus
  score: number | null
}

export interface TeacherSubmissionQuery {
  page?: number
  pageSize?: number
  studentName?: string
  classId?: number | null
}

export interface PaginatedTeacherSubmissions {
  items: TeacherSubmissionListItem[]
  total: number
  page: number
  pageSize: number
  totalPages: number
}

export interface TeacherSubQuestionDetail {
  subQuestionId: number
  content: string
  answerFormat: AnswerFormat
  referenceAnswer: string
  rubric: string
  maxScore: number
  aiScore: number | null
  effectiveScore: number | null
  usedSandbox: boolean
  aiEvidence: string
  gradingLog: string
  studentAnswerBlocks: SubQuestionAnswerBlocks
}

export type DisputeStatus = 0 | 1 | 2 | 3

export interface TeacherDisputeRound {
  id: number
  disputeReason: string
  disputeTime: string
  manualComment: string | null
  resolveTime: string | null
  withdrawnTime: string | null
}

export interface TeacherQuestionDetail {
  experimentQuestionId: number
  submissionId: number | null
  questionTitle: string
  questionContent: string
  effectivePoints: number
  status: QuestionStatus | null
  score: number | null
  disputeStatus: DisputeStatus
  disputeReason: string | null
  disputeTime: string | null
  manualComment: string | null
  disputeResolveTime: string | null
  disputes: TeacherDisputeRound[]
  subQuestions: TeacherSubQuestionDetail[]
}

export interface TeacherSubmissionRound {
  submitTime: string
  status: SubmissionRoundStatus
  score: number | null
  questions: TeacherQuestionDetail[]
}

export interface TeacherStudentSubmissionDetail {
  experimentId: number
  studentId: number
  experimentTitle: string
  studentName: string
  rounds: TeacherSubmissionRound[]
}

interface ApiListItem {
  experimentId: number
  experimentTitle: string | null
  classId?: number | null
  studentId: number
  studentName: string | null
  roundSubmitTime?: string
  submitTime?: string
  questionCount: number
  status: string
  score: number | null
}

interface ApiListResponse {
  data: ApiListItem[]
  total: number
}

interface ApiAnswerBlock {
  type?: string
  content?: string
  language?: string
  attachmentId?: number
}

interface ApiAnswerGroup {
  subQuestionId?: number
  blocks?: ApiAnswerBlock[]
}

interface ApiSubQuestion {
  subQuestionId?: number
  id?: number
  content?: string | null
  answerFormat?: string | null
  referenceAnswer?: string | null
  rubric?: string | null
  maxScore?: number | null
  score?: number | null
  aiScore?: number | null
  effectiveScore?: number | null
  usedSandbox?: boolean | null
  aiEvidence?: string | null
  comment?: string | null
  gradingLog?: string | null
  studentAnswerBlocks?: ApiAnswerGroup[] | ApiAnswerBlock[] | ApiAnswerGroup
  answerBlocks?: ApiAnswerGroup[] | ApiAnswerBlock[] | ApiAnswerGroup
}

interface ApiDisputeRound {
  id?: number
  disputeReason?: string | null
  disputeTime?: string | null
  manualComment?: string | null
  resolveTime?: string | null
  withdrawnTime?: string | null
}

interface ApiQuestion {
  experimentQuestionId?: number
  id?: number
  submissionId?: number | null
  questionTitle?: string | null
  title?: string | null
  questionContent?: string | null
  content?: string | null
  question?: { title?: string | null; content?: string | null } | null
  effectivePoints?: number | null
  status?: number | string | null
  score?: number | null
  disputeStatus?: number | null
  disputeReason?: string | null
  disputeTime?: string | null
  manualComment?: string | null
  disputeResolveTime?: string | null
  disputes?: ApiDisputeRound[] | null
  subQuestions?: ApiSubQuestion[] | null
}

interface ApiRound {
  roundSubmitTime?: string
  submitTime?: string
  status?: string
  score?: number | null
  questions?: ApiQuestion[] | null
}

interface ApiDetail {
  experimentId?: number
  studentId?: number
  experimentTitle?: string | null
  title?: string | null
  studentName?: string | null
  name?: string | null
  experiment?: { title?: string | null; experimentTitle?: string | null } | null
  student?: { name?: string | null; studentName?: string | null; realName?: string | null } | null
  rounds?: ApiRound[] | null
}

function normalizeRoundStatus(value: string | undefined | null): SubmissionRoundStatus {
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

function asNumber(value: unknown, fallback = 0): number {
  return typeof value === "number" && Number.isFinite(value) ? value : fallback
}

function asNullableNumber(value: unknown): number | null {
  return typeof value === "number" && Number.isFinite(value) ? value : null
}

function asString(value: unknown): string {
  return typeof value === "string" ? value : ""
}

function firstNonEmpty(...values: unknown[]): string {
  for (const value of values) {
    if (typeof value === "string" && value.trim()) {
      return value
    }
  }
  return ""
}

function normalizeListItem(item: ApiListItem): TeacherSubmissionListItem {
  const status = normalizeRoundStatus(item.status)
  const submitTime = item.roundSubmitTime ?? item.submitTime ?? ""
  const classId =
    typeof item.classId === "number" && item.classId > 0 ? item.classId : null

  return {
    experimentId: item.experimentId,
    experimentTitle: item.experimentTitle ?? "",
    classId,
    studentId: item.studentId,
    studentName: item.studentName ?? "",
    submitTime,
    questionCount: item.questionCount,
    status,
    score: status === "completed" ? item.score : null,
  }
}

export async function fetchTeacherSubmissions(
  params: TeacherSubmissionQuery,
  signal?: AbortSignal,
): Promise<PaginatedTeacherSubmissions> {
  const page = params.page ?? 1
  const pageSize = params.pageSize ?? PAGE_SIZE
  const search = new URLSearchParams({
    page: String(page),
    pageSize: String(pageSize),
  })

  if (params.studentName?.trim()) {
    search.set("studentName", params.studentName.trim())
  }
  if (params.classId != null) {
    search.set("classId", String(params.classId))
  }

  const json = await apiFetch<ApiListResponse>(`/experiments/submissions?${search}`, {
    signal,
  })
  const total = json.total ?? 0
  const totalPages = Math.max(1, Math.ceil(total / pageSize))

  return {
    items: (json.data ?? []).map(normalizeListItem),
    total,
    page,
    pageSize,
    totalPages,
  }
}

function isAnswerBlock(value: unknown): value is ApiAnswerBlock {
  return (
    typeof value === "object" &&
    value !== null &&
    "type" in value &&
    typeof (value as ApiAnswerBlock).type === "string"
  )
}

function isAnswerGroup(value: unknown): value is ApiAnswerGroup {
  return typeof value === "object" && value !== null && "blocks" in value
}

function normalizeBlock(block: ApiAnswerBlock): AnswerBlockItem | null {
  if (block.type === "text" || block.type === "code" || block.type === "image") {
    return {
      type: block.type,
      content: block.content,
      language: block.language,
      attachmentId: block.attachmentId,
    }
  }
  return null
}

function normalizeAnswerBlocks(
  raw: ApiSubQuestion["studentAnswerBlocks"],
  subQuestionId: number,
): SubQuestionAnswerBlocks {
  if (raw == null) {
    return { subQuestionId, blocks: [] }
  }

  if (Array.isArray(raw)) {
    if (raw.length === 0) {
      return { subQuestionId, blocks: [] }
    }
    if (isAnswerGroup(raw[0])) {
      const groups = raw as ApiAnswerGroup[]
      const match =
        groups.find((group) => group.subQuestionId === subQuestionId) ?? groups[0]
      const blocks = (match.blocks ?? []).map(normalizeBlock).filter((block) => block != null)
      return { subQuestionId, blocks }
    }
    if (isAnswerBlock(raw[0])) {
      const blocks = (raw as ApiAnswerBlock[]).map(normalizeBlock).filter((block) => block != null)
      return { subQuestionId, blocks }
    }
    return { subQuestionId, blocks: [] }
  }

  if (isAnswerGroup(raw)) {
    const blocks = (raw.blocks ?? []).map(normalizeBlock).filter((block) => block != null)
    return { subQuestionId, blocks }
  }

  return { subQuestionId, blocks: [] }
}

function inferAnswerFormat(
  explicit: string | null | undefined,
  blocks: SubQuestionAnswerBlocks,
): AnswerFormat {
  if (explicit === "text" || explicit === "image" || explicit === "code") {
    return explicit
  }
  const first = blocks.blocks[0]
  if (first?.type === "image" || first?.type === "code" || first?.type === "text") {
    return first.type
  }
  return "text"
}

function normalizeQuestionStatus(value: number | string | null | undefined): QuestionStatus | null {
  const numeric = typeof value === "string" ? Number(value) : value
  if (numeric === 0 || numeric === 1 || numeric === 2 || numeric === 3) {
    return numeric
  }
  return null
}

function deriveRoundStatus(questions: TeacherQuestionDetail[]): SubmissionRoundStatus {
  const questionCount = questions.length
  const failedCount = questions.filter((question) => question.status === 3).length
  const doneCount = questions.filter((question) => question.status === 2).length
  const gradingCount = questions.filter((question) => question.status === 1).length

  if (failedCount > 0) {
    return "grading_failed"
  }
  if (questionCount > 0 && doneCount === questionCount) {
    return "completed"
  }
  if (gradingCount > 0) {
    return "grading_in_progress"
  }
  return "pending"
}

function deriveRoundScore(
  status: SubmissionRoundStatus,
  questions: TeacherQuestionDetail[],
): number | null {
  if (status !== "completed") {
    return null
  }
  if (questions.some((question) => question.score == null)) {
    return null
  }
  return questions.reduce((sum, question) => sum + (question.score ?? 0), 0)
}

function normalizeSubQuestion(item: ApiSubQuestion): TeacherSubQuestionDetail {
  const subQuestionId = item.subQuestionId ?? item.id ?? 0
  const studentAnswerBlocks = normalizeAnswerBlocks(
    item.studentAnswerBlocks ?? item.answerBlocks,
    subQuestionId,
  )
  const maxScore = asNumber(item.maxScore, 0)

  return {
    subQuestionId,
    content: asString(item.content ?? ""),
    answerFormat: inferAnswerFormat(item.answerFormat, studentAnswerBlocks),
    referenceAnswer: asString(item.referenceAnswer ?? ""),
    rubric: asString(item.rubric ?? ""),
    maxScore,
    aiScore: asNullableNumber(item.aiScore),
    effectiveScore: asNullableNumber(item.effectiveScore),
    usedSandbox: item.usedSandbox === true,
    aiEvidence: asString(item.aiEvidence ?? item.comment ?? ""),
    gradingLog: asString(item.gradingLog ?? ""),
    studentAnswerBlocks,
  }
}

function blankToNull(value: unknown): string | null {
  if (typeof value !== "string") {
    return null
  }
  const trimmed = value.trim()
  return trimmed ? trimmed : null
}

function normalizeDisputeStatus(value: number | null | undefined): DisputeStatus {
  if (value === 0 || value === 1 || value === 2 || value === 3) {
    return value
  }
  return 0
}

function normalizeDisputeRound(item: ApiDisputeRound): TeacherDisputeRound {
  return {
    id: asNumber(item.id, 0),
    disputeReason: asString(item.disputeReason ?? ""),
    disputeTime: asString(item.disputeTime ?? ""),
    manualComment: blankToNull(item.manualComment),
    resolveTime: item.resolveTime ?? null,
    withdrawnTime: item.withdrawnTime ?? null,
  }
}

function normalizeQuestion(item: ApiQuestion, index: number): TeacherQuestionDetail {
  const subQuestions = (item.subQuestions ?? []).map(normalizeSubQuestion)
  const summedMax = subQuestions.reduce((sum, sub) => sum + sub.maxScore, 0)
  const questionTitle = firstNonEmpty(
    item.questionTitle,
    item.title,
    item.question?.title,
  )
  const disputes = (item.disputes ?? []).map(normalizeDisputeRound)
  const latest = disputes[0]

  return {
    experimentQuestionId: item.experimentQuestionId ?? item.id ?? index,
    submissionId: asNullableNumber(item.submissionId),
    questionTitle,
    questionContent: firstNonEmpty(item.questionContent, item.question?.content),
    effectivePoints: asNumber(item.effectivePoints, summedMax),
    status: normalizeQuestionStatus(item.status),
    score: asNullableNumber(item.score),
    disputeStatus: latest
      ? latest.withdrawnTime
        ? 3
        : latest.resolveTime
          ? 2
          : 1
      : normalizeDisputeStatus(item.disputeStatus),
    disputeReason: blankToNull(item.disputeReason) ?? (latest?.disputeReason.trim() ? latest.disputeReason : null),
    disputeTime: item.disputeTime ?? latest?.disputeTime ?? null,
    manualComment: blankToNull(item.manualComment) ?? latest?.manualComment ?? null,
    disputeResolveTime: item.disputeResolveTime ?? latest?.resolveTime ?? null,
    disputes,
    subQuestions,
  }
}

function normalizeRound(item: ApiRound): TeacherSubmissionRound {
  const questions = (item.questions ?? []).map(normalizeQuestion)
  const derivedStatus = deriveRoundStatus(questions)
  const status = item.status ? normalizeRoundStatus(item.status) : derivedStatus
  const derivedScore = deriveRoundScore(status, questions)
  const score =
    status === "completed"
      ? (asNullableNumber(item.score) ?? derivedScore)
      : null

  return {
    submitTime: item.roundSubmitTime ?? item.submitTime ?? "",
    status,
    score,
    questions,
  }
}

export async function fetchTeacherStudentSubmissions(
  experimentId: number,
  studentId: number,
  signal?: AbortSignal,
): Promise<TeacherStudentSubmissionDetail> {
  const json = await apiFetch<ApiDetail>(
    `/experiments/${experimentId}/students/${studentId}/submissions`,
    { signal },
  )

  let experiment = null
  try {
    experiment = await getExperimentById(experimentId, signal)
  } catch {
    experiment = null
  }

  const promptByExperimentQuestionId = new Map(
    (experiment?.questions ?? []).map((question) => [question.id, question]),
  )

  const normalizedRounds = (json.rounds ?? []).map(normalizeRound)
  const bankByQuestionId = new Map<number, { title: string; content: string }>()
  const bankIds = new Set<number>()
  for (const round of normalizedRounds) {
    for (const question of round.questions) {
      const prompt = promptByExperimentQuestionId.get(question.experimentQuestionId)
      const hasTitle = Boolean(firstNonEmpty(question.questionTitle, prompt?.questionTitle))
      const hasContent = Boolean(question.questionContent)
      if ((!hasTitle || !hasContent) && prompt && prompt.questionId > 0) {
        bankIds.add(prompt.questionId)
      }
    }
  }
  await Promise.all(
    [...bankIds].map(async (id) => {
      try {
        const bank = await getQuestionById(id, signal)
        if (bank) {
          bankByQuestionId.set(id, { title: bank.title, content: bank.content })
        }
      } catch {
        // Keep 015 fields if the question bank row is missing.
      }
    }),
  )

  const rounds = normalizedRounds.map((round) => ({
    ...round,
    questions: round.questions.map((question) => {
      const prompt = promptByExperimentQuestionId.get(question.experimentQuestionId)
      const bank = prompt ? bankByQuestionId.get(prompt.questionId) : undefined
      return {
        ...question,
        questionTitle: firstNonEmpty(question.questionTitle, prompt?.questionTitle, bank?.title),
        questionContent: firstNonEmpty(question.questionContent, bank?.content),
      }
    }),
  }))

  return {
    experimentId: json.experimentId ?? experimentId,
    studentId: json.studentId ?? studentId,
    experimentTitle: firstNonEmpty(
      json.experimentTitle,
      json.title,
      json.experiment?.experimentTitle,
      json.experiment?.title,
      experiment?.title,
    ),
    studentName: firstNonEmpty(
      json.studentName,
      json.name,
      json.student?.studentName,
      json.student?.realName,
      json.student?.name,
    ),
    rounds,
  }
}
