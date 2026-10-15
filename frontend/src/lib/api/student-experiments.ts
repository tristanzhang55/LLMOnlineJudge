import { apiFetch, ApiError } from "@/lib/api/client"
import { PAGE_SIZE } from "@/lib/mock-assignments"

export interface AnswerBlockItem {
  type: "text" | "code" | "image"
  content?: string
  language?: string
  attachmentId?: number
}

export interface SubQuestionAnswerBlocks {
  subQuestionId: number
  blocks: AnswerBlockItem[]
}

export interface StudentSubQuestion {
  id: number
  orderIndex: number
  content: string
  answerFormat: "text" | "image" | "code"
  score: number
}

export interface StudentExperimentQuestion {
  id: number
  experimentId: number
  questionId: number
  questionTitle: string
  questionContent: string
  effectivePoints: number
  sortOrder: number
  subQuestions: StudentSubQuestion[]
  currentAnswerBlocks: SubQuestionAnswerBlocks[] | null
  prefillAnswerBlocks: SubQuestionAnswerBlocks[] | null
}

export interface StudentHistoryRound {
  submitTime: string
  roundLabel: string
  answers: Array<{
    experimentQuestionId: number
    submitTime: string
    answerBlocks: SubQuestionAnswerBlocks[]
  }>
}

export interface StudentExperimentListItem {
  id: number
  title: string
  description: string | null
  classId: number | null
  className: string | null
  status: 1 | 2
  deadlineTime: string | null
  lastPublishedTime: string | null
  locked: boolean
  resubmissionEligible: boolean
}

export interface StudentExperimentDetail extends StudentExperimentListItem {
  questions: StudentExperimentQuestion[]
  history: StudentHistoryRound[]
}

export type SubmissionAnswerSource = "draft" | "current" | "prefill" | "empty"

export interface MySubmissionQuestionState {
  experimentQuestionId: number
  submissionId: number | null
  answerBlocks: SubQuestionAnswerBlocks[] | null
  source: SubmissionAnswerSource
}

export interface MySubmissionState {
  id: number
  status: 1 | 2
  locked: boolean
  resubmissionEligible: boolean
  isRoundGradingComplete: boolean
  editable: boolean
  questions: MySubmissionQuestionState[]
}

export interface ConfirmSubmitResult {
  submitTime: string
  submissions: Array<{ experimentQuestionId: number; submissionId: number }>
}

export interface StudentImageAnswer {
  attachmentId: number
  url: string
}

export type StudentFormValues = Record<number, Record<number, string>>
export type StudentImageValues = Record<
  number,
  Record<number, StudentImageAnswer | null>
>
export type StudentCodeLanguages = Record<number, Record<number, string>>

export interface MySubQuestionScore {
  subQuestionId: number
  maxScore: number
  effectiveScore: number | null
  comment: string
}

export type DisputeStatus = 0 | 1 | 2 | 3

export interface MyQuestionScores {
  experimentQuestionId: number
  submissionId: number | null
  status: 0 | 1 | 2 | 3 | null
  score: number | null
  disputeId: number | null
  disputeStatus: DisputeStatus
  disputeReason: string | null
  disputeTime: string | null
  manualComment: string | null
  disputeResolveTime: string | null
  subQuestions: MySubQuestionScore[]
}

export interface FileDisputeResult {
  id: number
  submissionId: number
  disputeStatus: DisputeStatus
  disputeReason: string
  disputeTime: string
  manualComment: string | null
  resolveTime: string | null
  withdrawnTime: string | null
}

export interface MyExperimentScores {
  experimentId: number
  isRoundGradingComplete: boolean
  questions: MyQuestionScores[]
}

interface ApiStudentExperimentListItem {
  id: number
  title: string | null
  description: string | null
  classId: number | null
  className?: string | null
  status: number
  deadlineTime: string | null
  lastPublishedTime: string | null
  locked: boolean
  resubmissionEligible: boolean
}

interface ApiStudentSubQuestion {
  id: number
  orderIndex: number
  content: string
  answerFormat: "text" | "image" | "code"
  score: number
}

interface ApiStudentExperimentQuestion {
  id: number
  experimentId: number
  questionId: number
  questionTitle: string | null
  questionContent: string | null
  effectivePoints: number
  sortOrder: number
  subQuestions: ApiStudentSubQuestion[]
  currentAnswerBlocks: SubQuestionAnswerBlocks[] | null
  prefillAnswerBlocks: SubQuestionAnswerBlocks[] | null
}

interface ApiStudentHistoryRound {
  submitTime: string
  roundLabel: string
  answers: Array<{
    experimentQuestionId: number
    submitTime: string
    answerBlocks: SubQuestionAnswerBlocks[]
  }>
}

interface ApiStudentExperimentDetail extends ApiStudentExperimentListItem {
  questions: ApiStudentExperimentQuestion[]
  history: ApiStudentHistoryRound[]
}

interface StudentExperimentListResponse {
  data: ApiStudentExperimentListItem[]
  total: number
}

interface ApiMySubmissionQuestionState {
  experimentQuestionId: number
  submissionId: number | null
  answerBlocks: SubQuestionAnswerBlocks[] | null
  source?: string
}

interface ApiMySubmissionState {
  id: number
  status: number
  locked: boolean
  resubmissionEligible: boolean
  isRoundGradingComplete: boolean
  editable: boolean
  questions: ApiMySubmissionQuestionState[]
}

interface ApiMySubQuestionScore {
  subQuestionId: number
  maxScore: number
  effectiveScore: number | null
  comment?: string | null
}

interface ApiMyQuestionScores {
  experimentQuestionId: number
  submissionId: number | null
  status: number | null
  score: number | null
  disputeId?: number | null
  disputeStatus?: number | null
  disputeReason?: string | null
  disputeTime?: string | null
  manualComment?: string | null
  disputeResolveTime?: string | null
  subQuestions?: ApiMySubQuestionScore[] | null
}

export interface ApiFileDisputeResult {
  id: number
  submissionId: number
  disputeStatus?: number | null
  disputeReason?: string | null
  disputeTime?: string | null
  manualComment?: string | null
  resolveTime?: string | null
  withdrawnTime?: string | null
}

interface ApiMyExperimentScores {
  experimentId: number
  isRoundGradingComplete: boolean
  questions: ApiMyQuestionScores[]
}

export interface PaginatedStudentExperimentList {
  items: StudentExperimentListItem[]
  total: number
  page: number
  pageSize: number
  totalPages: number
}

function normalizeStudentStatus(value: number): 1 | 2 {
  return value === 2 ? 2 : 1
}

function normalizeSource(value: string | undefined): SubmissionAnswerSource {
  if (value === "draft" || value === "current" || value === "prefill" || value === "empty") {
    return value
  }
  return "empty"
}

function normalizeStudentListItem(item: ApiStudentExperimentListItem): StudentExperimentListItem {
  return {
    id: item.id,
    title: item.title ?? "",
    description: item.description,
    classId: item.classId,
    className: item.className?.trim() ? item.className : null,
    status: normalizeStudentStatus(item.status),
    deadlineTime: item.deadlineTime,
    lastPublishedTime: item.lastPublishedTime,
    locked: item.locked,
    resubmissionEligible: item.resubmissionEligible,
  }
}

function normalizeStudentQuestion(item: ApiStudentExperimentQuestion): StudentExperimentQuestion {
  return {
    id: item.id,
    experimentId: item.experimentId,
    questionId: item.questionId,
    questionTitle: item.questionTitle ?? "",
    questionContent: item.questionContent ?? "",
    effectivePoints: item.effectivePoints,
    sortOrder: item.sortOrder,
    subQuestions: item.subQuestions ?? [],
    currentAnswerBlocks: item.currentAnswerBlocks,
    prefillAnswerBlocks: item.prefillAnswerBlocks,
  }
}

function normalizeStudentDetail(item: ApiStudentExperimentDetail): StudentExperimentDetail {
  return {
    ...normalizeStudentListItem(item),
    questions: (item.questions ?? []).map(normalizeStudentQuestion),
    history: item.history ?? [],
  }
}

function normalizeSubmissionStatus(value: number | null | undefined): 0 | 1 | 2 | 3 | null {
  if (value === 0 || value === 1 || value === 2 || value === 3) {
    return value
  }
  return null
}

function normalizeMySubQuestionScore(item: ApiMySubQuestionScore): MySubQuestionScore {
  return {
    subQuestionId: item.subQuestionId,
    maxScore: item.maxScore ?? 0,
    effectiveScore: item.effectiveScore ?? null,
    comment: item.comment?.trim() ? item.comment : "",
  }
}

export function normalizeDisputeStatus(value: number | null | undefined): DisputeStatus {
  if (value === 0 || value === 1 || value === 2 || value === 3) {
    return value
  }
  return 0
}

function blankToNull(value: string | null | undefined): string | null {
  const trimmed = value?.trim()
  return trimmed ? trimmed : null
}

function normalizeDisputeId(value: number | null | undefined): number | null {
  if (typeof value === "number" && Number.isSafeInteger(value) && value > 0) {
    return value
  }
  return null
}

export function mapDisputeWriteResult(item: ApiFileDisputeResult): FileDisputeResult {
  return {
    id: item.id,
    submissionId: item.submissionId,
    disputeStatus: normalizeDisputeStatus(item.disputeStatus),
    disputeReason: item.disputeReason?.trim() ? item.disputeReason : "",
    disputeTime: item.disputeTime ?? "",
    manualComment: blankToNull(item.manualComment),
    resolveTime: item.resolveTime ?? null,
    withdrawnTime: item.withdrawnTime ?? null,
  }
}

function normalizeMyQuestionScores(item: ApiMyQuestionScores): MyQuestionScores {
  return {
    experimentQuestionId: item.experimentQuestionId,
    submissionId: item.submissionId ?? null,
    status: normalizeSubmissionStatus(item.status),
    score: item.score ?? null,
    disputeId: normalizeDisputeId(item.disputeId),
    disputeStatus: normalizeDisputeStatus(item.disputeStatus),
    disputeReason: blankToNull(item.disputeReason),
    disputeTime: item.disputeTime ?? null,
    manualComment: blankToNull(item.manualComment),
    disputeResolveTime: item.disputeResolveTime ?? null,
    subQuestions: (item.subQuestions ?? []).map(normalizeMySubQuestionScore),
  }
}

export function canFileDispute(
  status: 0 | 1 | 2 | 3 | null,
  disputeStatus: DisputeStatus,
  submissionId: number | null,
): boolean {
  return (status === 2 || status === 3) && disputeStatus !== 1 && submissionId != null
}

function normalizeMyExperimentScores(item: ApiMyExperimentScores): MyExperimentScores {
  return {
    experimentId: item.experimentId,
    isRoundGradingComplete: item.isRoundGradingComplete,
    questions: (item.questions ?? []).map(normalizeMyQuestionScores),
  }
}

function normalizeMySubmissionState(item: ApiMySubmissionState): MySubmissionState {
  return {
    id: item.id,
    status: normalizeStudentStatus(item.status),
    locked: item.locked,
    resubmissionEligible: item.resubmissionEligible,
    isRoundGradingComplete: item.isRoundGradingComplete,
    editable: item.editable,
    questions: (item.questions ?? []).map((question) => ({
      experimentQuestionId: question.experimentQuestionId,
      submissionId: question.submissionId ?? null,
      answerBlocks: question.answerBlocks,
      source:
        question.answerBlocks == null && question.source == null
          ? "empty"
          : normalizeSource(question.source),
    })),
  }
}

export async function fetchStudentExperiments(
  params: { page?: number; pageSize?: number },
  signal?: AbortSignal,
): Promise<PaginatedStudentExperimentList> {
  const page = params.page ?? 1
  const pageSize = params.pageSize ?? PAGE_SIZE
  const search = new URLSearchParams({
    page: String(page),
    pageSize: String(pageSize),
  })

  const json = await apiFetch<StudentExperimentListResponse>(
    `/student/experiments?${search}`,
    { signal },
  )
  const total = json.total ?? 0
  const totalPages = Math.max(1, Math.ceil(total / pageSize))

  return {
    items: (json.data ?? []).map(normalizeStudentListItem),
    total,
    page,
    pageSize,
    totalPages,
  }
}

export async function getStudentExperimentById(
  id: number,
  signal?: AbortSignal,
): Promise<StudentExperimentDetail | null> {
  try {
    const item = await apiFetch<ApiStudentExperimentDetail>(`/student/experiments/${id}`, {
      signal,
    })
    return normalizeStudentDetail(item)
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) {
      return null
    }
    throw error
  }
}

export async function fetchMySubmissionState(
  experimentId: number,
  signal?: AbortSignal,
): Promise<MySubmissionState> {
  const item = await apiFetch<ApiMySubmissionState>(
    `/experiments/${experimentId}/my-submission-state`,
    { signal },
  )
  return normalizeMySubmissionState(item)
}

export async function fetchMyExperimentScores(
  experimentId: number,
  signal?: AbortSignal,
): Promise<MyExperimentScores> {
  const item = await apiFetch<ApiMyExperimentScores>(
    `/student/experiments/${experimentId}/my-scores`,
    { signal },
  )
  return normalizeMyExperimentScores(item)
}

export async function fileStudentDispute(
  experimentId: number,
  submissionId: number,
  disputeReason: string,
): Promise<FileDisputeResult> {
  const item = await apiFetch<ApiFileDisputeResult>(
    `/student/experiments/${experimentId}/submissions/${submissionId}/disputes`,
    {
      method: "POST",
      body: JSON.stringify({ disputeReason: disputeReason.trim() }),
    },
  )
  return mapDisputeWriteResult(item)
}

export function indexScoresBySubQuestion(
  scores: MyExperimentScores,
): Map<string, MySubQuestionScore> {
  const map = new Map<string, MySubQuestionScore>()
  for (const question of scores.questions) {
    for (const subQuestion of question.subQuestions) {
      map.set(`${question.experimentQuestionId}:${subQuestion.subQuestionId}`, subQuestion)
    }
  }
  return map
}

export function formatAwardedScore(value: number | null | undefined): string {
  if (value == null) {
    return "—"
  }
  if (Number.isInteger(value)) {
    return String(value)
  }
  return String(value)
}

export async function saveSubmissionDraft(body: {
  experimentQuestionId: number
  answerBlocks: SubQuestionAnswerBlocks[]
}): Promise<{ id: number }> {
  return apiFetch<{ id: number }>("/submissions/draft", {
    method: "PUT",
    body: JSON.stringify(body),
  })
}

export async function submitStudentExperiment(
  id: number,
  body: {
    answers: Array<{ experimentQuestionId: number; answerBlocks: SubQuestionAnswerBlocks[] }>
  },
): Promise<ConfirmSubmitResult> {
  return apiFetch<ConfirmSubmitResult>(`/experiments/${id}/submit`, {
    method: "POST",
    body: JSON.stringify(body),
  })
}

export function answerBlocksToFormValues(
  blocks: SubQuestionAnswerBlocks[] | null,
): Record<number, string> {
  const result: Record<number, string> = {}
  for (const group of blocks ?? []) {
    const textBlock = group.blocks.find((block) => block.type === "text" || block.type === "code")
    if (textBlock?.content) {
      result[group.subQuestionId] = textBlock.content
    }
  }
  return result
}

export function answerBlocksToImageValues(
  blocks: SubQuestionAnswerBlocks[] | null,
): Record<number, StudentImageAnswer | null> {
  const result: Record<number, StudentImageAnswer | null> = {}
  for (const group of blocks ?? []) {
    const imageBlock = group.blocks.find((block) => block.type === "image")
    if (imageBlock?.attachmentId != null && imageBlock.content) {
      result[group.subQuestionId] = {
        attachmentId: imageBlock.attachmentId,
        url: imageBlock.content,
      }
    }
  }
  return result
}

export function answerBlocksToCodeLanguages(
  blocks: SubQuestionAnswerBlocks[] | null,
): Record<number, string> {
  const result: Record<number, string> = {}
  for (const group of blocks ?? []) {
    const codeBlock = group.blocks.find((block) => block.type === "code" && block.language)
    if (codeBlock?.language) {
      result[group.subQuestionId] = codeBlock.language
    }
  }
  return result
}

export function serializeAnswerBlocks(blocks: SubQuestionAnswerBlocks[]): string {
  return JSON.stringify(blocks)
}

export function buildAnswersForQuestion(
  question: StudentExperimentQuestion,
  textValues: Record<number, string>,
  imageValues: Record<number, StudentImageAnswer | null>,
  codeLanguages: Record<number, string> = {},
): SubQuestionAnswerBlocks[] {
  const answerBlocks: SubQuestionAnswerBlocks[] = []

  for (const subQuestion of question.subQuestions) {
    if (subQuestion.answerFormat === "image") {
      const image = imageValues[subQuestion.id]
      if (!image) {
        continue
      }
      answerBlocks.push({
        subQuestionId: subQuestion.id,
        blocks: [
          {
            type: "image",
            attachmentId: image.attachmentId,
            content: image.url,
          },
        ],
      })
      continue
    }

    const content = textValues[subQuestion.id]?.trim()
    if (!content) {
      continue
    }

    const isCode = subQuestion.answerFormat === "code"
    const language = isCode ? codeLanguages[subQuestion.id] : undefined
    answerBlocks.push({
      subQuestionId: subQuestion.id,
      blocks: [
        {
          type: isCode ? "code" : "text",
          content,
          ...(language ? { language } : {}),
        },
      ],
    })
  }

  return answerBlocks
}

export function buildSubmitAnswers(
  questions: StudentExperimentQuestion[],
  formValues: StudentFormValues,
  imageValues: StudentImageValues,
  codeLanguages: StudentCodeLanguages = {},
): Array<{ experimentQuestionId: number; answerBlocks: SubQuestionAnswerBlocks[] }> {
  const answers: Array<{ experimentQuestionId: number; answerBlocks: SubQuestionAnswerBlocks[] }> =
    []

  for (const question of questions) {
    const answerBlocks = buildAnswersForQuestion(
      question,
      formValues[question.id] ?? {},
      imageValues[question.id] ?? {},
      codeLanguages[question.id] ?? {},
    )

    if (answerBlocks.length > 0) {
      answers.push({
        experimentQuestionId: question.id,
        answerBlocks,
      })
    }
  }

  if (answers.length === 0 && questions.length > 0) {
    answers.push({
      experimentQuestionId: questions[0].id,
      answerBlocks: [],
    })
  }

  return answers
}

export function formatHistoryAnswerText(
  blocks: SubQuestionAnswerBlocks[],
  labels: { image: string; empty: string },
): string {
  const parts: string[] = []

  for (const group of blocks) {
    for (const block of group.blocks) {
      if (block.type === "text" || block.type === "code") {
        if (block.content?.trim()) {
          parts.push(block.content.trim())
        }
      } else if (block.type === "image") {
        parts.push(labels.image)
      }
    }
  }

  return parts.length > 0 ? parts.join("\n") : labels.empty
}
