import { getMockQuestionById, type Question } from "@/lib/mock-questions"

export type ExperimentStatus = 0 | 1 | 2

export interface ExperimentQuestion {
  id: number
  experimentId: number
  questionId: number
  points: number
  sortOrder: number
  createTime: string
  updateTime: string
  isDelete: 0 | 1
  title: string
  subQuestionCount: number
  questionTotalScore: number
}

export interface Experiment {
  id: number
  title: string
  description?: string
  classId: number
  status: ExperimentStatus
  userId: number
  createTime: string
  updateTime: string
  deadlineTime: string
  isDelete: 0 | 1
  questions: ExperimentQuestion[]
}

export interface SelectedQuestionFormValue {
  id?: number
  questionId: number
  title: string
  subQuestionCount: number
  questionTotalScore: number
  points: number
  pointsOverridden: boolean
  sortOrder: number
  questionDeleted?: boolean
  hasSubmissions?: boolean
}

export interface AssignmentFormPayload {
  title: string
  description: string
  classId: number | null
  deadlineTime: string
  questions: SelectedQuestionFormValue[]
  submittedStudentCount?: number
}

export interface PaginatedExperiments {
  items: Experiment[]
  total: number
  page: number
  pageSize: number
  totalPages: number
}

export const PAGE_SIZE = 10

export const EXPERIMENT_STATUSES: ExperimentStatus[] = [0, 1, 2]

export const STATUS_BADGE_CLASS: Record<ExperimentStatus, string> = {
  0: "border-transparent bg-muted text-muted-foreground",
  1: "border-transparent bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400",
  2: "border-transparent bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-400",
}

const ASSIGNMENT_TITLES = [
  "Linux 基础实验一",
  "Shell 脚本综合练习",
  "文件与权限实验",
  "进程管理实验",
  "C 语言指针实验",
  "链表与数据结构",
  "网络编程实验",
  "期中综合测验",
  "期末大作业",
  "grep 与 awk 实战",
  "Makefile 实验",
  "多线程编程实验",
  "系统调用实验",
  "操作系统问答作业",
  "cron 定时任务实验",
]

function delay(ms = 80): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

function formatDeadline(daysFromNow: number, hour = 23, minute = 59): string {
  const date = new Date()
  date.setDate(date.getDate() + daysFromNow)
  date.setHours(hour, minute, 0, 0)
  return date.toISOString()
}

function joinQuestionMeta(
  experimentId: number,
  questionId: number,
  points: number,
  sortOrder: number,
  timestamp: string,
  eqId: number
): ExperimentQuestion | null {
  const question = questionsCache.get(questionId)
  if (!question) {
    return null
  }

  return {
    id: eqId,
    experimentId,
    questionId,
    points,
    sortOrder,
    createTime: timestamp,
    updateTime: timestamp,
    isDelete: 0,
    title: question.title,
    subQuestionCount: question.subQuestions.length,
    questionTotalScore: question.totalScore,
  }
}

const questionsCache = new Map<number, Question>()

async function ensureQuestionsCached(): Promise<void> {
  if (questionsCache.size > 0) {
    return
  }

  for (let id = 1; id <= 28; id++) {
    const question = await getMockQuestionById(id)
    if (question) {
      questionsCache.set(id, question)
    }
  }
}

function buildSeedExperiments(): Experiment[] {
  const baseTime = new Date(Date.UTC(2026, 7, 1, 8, 0, 0)).toISOString()
  const configs: Array<{
    title: string
    classId: number
    status: ExperimentStatus
    deadlineDays: number
    questionIds: Array<{ questionId: number; points: number }>
    description?: string
  }> = [
    {
      title: ASSIGNMENT_TITLES[0],
      classId: 1,
      status: 0,
      deadlineDays: 14,
      questionIds: [{ questionId: 1, points: 0 }, { questionId: 2, points: 0 }],
      description: "完成 Linux 基础命令练习。",
    },
    {
      title: ASSIGNMENT_TITLES[1],
      classId: 1,
      status: 1,
      deadlineDays: 2,
      questionIds: [{ questionId: 3, points: 0 }, { questionId: 4, points: 25 }, { questionId: 5, points: 0 }],
    },
    {
      title: ASSIGNMENT_TITLES[2],
      classId: 2,
      status: 1,
      deadlineDays: 7,
      questionIds: [{ questionId: 6, points: 0 }],
    },
    {
      title: ASSIGNMENT_TITLES[3],
      classId: 2,
      status: 2,
      deadlineDays: -5,
      questionIds: [{ questionId: 7, points: 0 }, { questionId: 8, points: 30 }],
    },
    {
      title: ASSIGNMENT_TITLES[4],
      classId: 3,
      status: 0,
      deadlineDays: 10,
      questionIds: [{ questionId: 9, points: 0 }, { questionId: 10, points: 0 }],
    },
    {
      title: ASSIGNMENT_TITLES[5],
      classId: 3,
      status: 0,
      deadlineDays: 21,
      questionIds: [{ questionId: 11, points: 0 }],
    },
    {
      title: ASSIGNMENT_TITLES[6],
      classId: 4,
      status: 1,
      deadlineDays: 1,
      questionIds: [
        { questionId: 12, points: 0 },
        { questionId: 13, points: 0 },
        { questionId: 14, points: 15 },
      ],
    },
    {
      title: ASSIGNMENT_TITLES[7],
      classId: 1,
      status: 2,
      deadlineDays: -10,
      questionIds: [
        { questionId: 15, points: 0 },
        { questionId: 16, points: 0 },
        { questionId: 17, points: 0 },
        { questionId: 18, points: 0 },
      ],
    },
    {
      title: ASSIGNMENT_TITLES[8],
      classId: 5,
      status: 0,
      deadlineDays: 30,
      questionIds: [{ questionId: 19, points: 0 }, { questionId: 20, points: 50 }],
    },
    {
      title: ASSIGNMENT_TITLES[9],
      classId: 2,
      status: 1,
      deadlineDays: 3,
      questionIds: [{ questionId: 21, points: 0 }],
    },
    {
      title: ASSIGNMENT_TITLES[10],
      classId: 3,
      status: 0,
      deadlineDays: 5,
      questionIds: [{ questionId: 22, points: 0 }, { questionId: 23, points: 0 }],
    },
    {
      title: ASSIGNMENT_TITLES[11],
      classId: 4,
      status: 1,
      deadlineDays: 12,
      questionIds: [{ questionId: 24, points: 0 }, { questionId: 25, points: 20 }],
    },
    {
      title: ASSIGNMENT_TITLES[12],
      classId: 5,
      status: 0,
      deadlineDays: 18,
      questionIds: [{ questionId: 26, points: 0 }],
    },
    {
      title: ASSIGNMENT_TITLES[13],
      classId: 1,
      status: 1,
      deadlineDays: -1,
      questionIds: [{ questionId: 27, points: 0 }, { questionId: 28, points: 0 }],
    },
    {
      title: ASSIGNMENT_TITLES[14],
      classId: 2,
      status: 2,
      deadlineDays: -3,
      questionIds: [{ questionId: 1, points: 0 }, { questionId: 2, points: 0 }, { questionId: 3, points: 0 }],
    },
  ]

  let eqId = 1

  return configs.map((config, index) => {
    const id = index + 1
    const timestamp = baseTime
    const questions = config.questionIds
      .map((item, sortOrder) => {
        const eq = joinQuestionMeta(id, item.questionId, item.points, sortOrder, timestamp, eqId++)
        return eq
      })
      .filter((item): item is ExperimentQuestion => item !== null)

    return {
      id,
      title: config.title,
      description: config.description,
      classId: config.classId,
      status: config.status,
      userId: 1,
      createTime: timestamp,
      updateTime: timestamp,
      deadlineTime: formatDeadline(config.deadlineDays),
      isDelete: 0,
      questions,
    }
  })
}

let experiments: Experiment[] = []
let nextExperimentId = 16
let nextExperimentQuestionId = 100
let seedInitialized = false

async function ensureSeed(): Promise<void> {
  if (seedInitialized) {
    return
  }
  await ensureQuestionsCached()
  experiments = buildSeedExperiments()
  nextExperimentQuestionId =
    Math.max(...experiments.flatMap((exp) => exp.questions.map((q) => q.id)), 0) + 1
  seedInitialized = true
}

export function formatDeadlineDisplay(iso: string): string {
  const date = new Date(iso)
  const pad = (value: number) => String(value).padStart(2, "0")
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

export function isDueSoon(deadline: string, status: ExperimentStatus): boolean {
  if (status !== 1) {
    return false
  }
  const diff = new Date(deadline).getTime() - Date.now()
  return diff > 0 && diff <= 3 * 24 * 60 * 60 * 1000
}

export function getEffectivePoints(row: {
  points: number
  questionTotalScore: number
}): number {
  return row.points === 0 ? row.questionTotalScore : row.points
}

export function computeAssignmentTotalScore(
  rows: Array<{ points: number; questionTotalScore: number }>
): number {
  return rows.reduce((sum, row) => sum + getEffectivePoints(row), 0)
}

function buildExperimentQuestions(
  experimentId: number,
  rows: SelectedQuestionFormValue[],
  timestamp: string
): ExperimentQuestion[] {
  return rows.map((row, index) => ({
    id: row.id ?? nextExperimentQuestionId++,
    experimentId,
    questionId: row.questionId,
    points: row.pointsOverridden ? row.points : 0,
    sortOrder: index,
    createTime: timestamp,
    updateTime: timestamp,
    isDelete: 0 as const,
    title: row.title,
    subQuestionCount: row.subQuestionCount,
    questionTotalScore: row.questionTotalScore,
  }))
}

export async function fetchExperiments(params: {
  page: number
  title?: string
  classId?: number | null
  status?: ExperimentStatus | null
}): Promise<PaginatedExperiments> {
  await ensureSeed()
  await delay()

  const filtered = experiments.filter((experiment) => {
    if (experiment.isDelete === 1) {
      return false
    }
    if (params.title?.trim() && !experiment.title.includes(params.title.trim())) {
      return false
    }
    if (params.classId != null && experiment.classId !== params.classId) {
      return false
    }
    if (params.status != null && experiment.status !== params.status) {
      return false
    }
    return true
  })

  const total = filtered.length
  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE))
  const page = Math.min(Math.max(1, params.page), totalPages)
  const start = (page - 1) * PAGE_SIZE

  return {
    items: filtered.slice(start, start + PAGE_SIZE).map((item) => ({
      ...item,
      questions: [...item.questions].sort((a, b) => a.sortOrder - b.sortOrder),
    })),
    total,
    page,
    pageSize: PAGE_SIZE,
    totalPages: total === 0 ? 1 : totalPages,
  }
}

export async function getExperimentById(id: number): Promise<Experiment | null> {
  await ensureSeed()
  await delay()

  const experiment = experiments.find((item) => item.id === id && item.isDelete === 0)
  if (!experiment) {
    return null
  }

  return {
    ...experiment,
    questions: [...experiment.questions]
      .filter((item) => item.isDelete === 0)
      .sort((a, b) => a.sortOrder - b.sortOrder),
  }
}

export async function createExperiment(
  payload: AssignmentFormPayload
): Promise<Experiment> {
  await ensureSeed()
  await delay()

  const now = new Date().toISOString()
  const id = nextExperimentId++
  const questions = buildExperimentQuestions(id, payload.questions, now)

  const experiment: Experiment = {
    id,
    title: payload.title.trim(),
    description: payload.description.trim() || undefined,
    classId: payload.classId!,
    status: 0,
    userId: 1,
    createTime: now,
    updateTime: now,
    deadlineTime: payload.deadlineTime,
    isDelete: 0,
    questions,
  }

  experiments = [experiment, ...experiments]
  return experiment
}

export async function updateExperiment(
  id: number,
  payload: AssignmentFormPayload
): Promise<Experiment | null> {
  await ensureSeed()
  await delay()

  const index = experiments.findIndex((item) => item.id === id && item.isDelete === 0)
  if (index === -1) {
    return null
  }

  const now = new Date().toISOString()
  const questions = buildExperimentQuestions(id, payload.questions, now)
  const updated: Experiment = {
    ...experiments[index],
    title: payload.title.trim(),
    description: payload.description.trim() || undefined,
    classId: payload.classId!,
    deadlineTime: payload.deadlineTime,
    updateTime: now,
    questions,
  }

  experiments[index] = updated
  return updated
}

export function experimentToFormPayload(experiment: Experiment): AssignmentFormPayload {
  return {
    title: experiment.title,
    description: experiment.description ?? "",
    classId: experiment.classId,
    deadlineTime: experiment.deadlineTime,
    questions: experiment.questions.map((item) => ({
      id: item.id,
      questionId: item.questionId,
      title: item.title,
      subQuestionCount: item.subQuestionCount,
      questionTotalScore: item.questionTotalScore,
      points: item.points === 0 ? item.questionTotalScore : item.points,
      pointsOverridden: item.points !== 0,
      sortOrder: item.sortOrder,
    })),
  }
}

export const defaultAssignmentFormValues: AssignmentFormPayload = {
  title: "",
  description: "",
  classId: null,
  deadlineTime: "",
  questions: [],
}

export async function getTotalExperimentCount(): Promise<number> {
  await ensureSeed()
  return experiments.filter((item) => item.isDelete === 0).length
}
