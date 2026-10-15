export type QuestionType = "shell_script" | "c_program" | "qa"
export type QuestionDifficulty = "easy" | "medium" | "hard"
export type AnswerFormat = "image" | "text" | "code"

export interface SubQuestion {
  id: number
  questionId: number
  orderIndex: number
  content: string
  answerFormat: AnswerFormat
  language?: string | null
  referenceAnswer?: string
  rubric?: string
  allowSandbox: boolean
  score: number
  createTime: string
  updateTime: string
  isDelete: 0 | 1
}

export interface Question {
  id: number
  title: string
  content: string
  type: QuestionType
  difficulty: QuestionDifficulty
  tags?: string
  totalScore: number
  submitNum: number
  userId: number
  createTime: string
  updateTime: string
  isDelete: 0 | 1
  subQuestions: SubQuestion[]
}

export interface SubQuestionFormValue {
  id?: number
  orderIndex: number
  content: string
  answerFormat: AnswerFormat
  language?: string
  referenceAnswer?: string
  rubric?: string
  allowSandbox: boolean
  score: number
}

export interface QuestionFormPayload {
  title: string
  content: string
  type: QuestionType
  difficulty: QuestionDifficulty
  tags?: string
  subQuestions: SubQuestionFormValue[]
}

export interface PaginatedQuestions {
  items: Question[]
  total: number
  page: number
  pageSize: number
  totalPages: number
}

export const PAGE_SIZE = 10

export const QUESTION_TYPES: QuestionType[] = ["shell_script", "c_program", "qa"]
export const QUESTION_DIFFICULTIES: QuestionDifficulty[] = ["easy", "medium", "hard"]
export const ANSWER_FORMATS: AnswerFormat[] = ["image", "text", "code"]

const SANDBOX_LANGUAGE_LABELS: Record<string, string> = {
  bash: "Bash",
  c: "C",
}

export function sandboxLanguageLabel(code: string): string {
  return SANDBOX_LANGUAGE_LABELS[code] ?? code
}

export const DIFFICULTY_BADGE_CLASS: Record<QuestionDifficulty, string> = {
  easy: "border-transparent bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400",
  medium: "border-transparent bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-400",
  hard: "border-transparent bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-400",
}

const TITLES = [
  "Shell 脚本练习",
  "文件权限管理",
  "进程与信号",
  "管道与重定向",
  "grep 与正则表达式",
  "C 语言指针基础",
  "C 语言结构体",
  "内存分配与释放",
  "链表实现",
  "排序算法实现",
  "Linux 目录结构",
  "用户与组管理",
  "cron 定时任务",
  "网络基础命令",
  "Shell 循环与条件",
  "awk 文本处理",
  "sed 流编辑器",
  "Makefile 编写",
  "调试 GDB 入门",
  "系统调用 write/read",
  "多线程编程",
  "socket 编程基础",
  "虚拟内存概念",
  "文件描述符",
  "环境变量",
  "bash 函数",
  "编译链接过程",
  "操作系统概述问答",
]

const TYPES: QuestionType[] = ["shell_script", "c_program", "qa"]
const DIFFICULTIES: QuestionDifficulty[] = ["easy", "medium", "hard"]

function computeTotalScore(subQuestions: SubQuestionFormValue[]): number {
  return subQuestions.reduce((sum, item) => sum + (item.score || 0), 0)
}

function buildSubQuestions(
  questionId: number,
  count: number,
  baseTime: string
): SubQuestion[] {
  return Array.from({ length: count }, (_, index) => {
    const subId = questionId * 100 + index + 1
    const answerFormat = ANSWER_FORMATS[(questionId + index) % ANSWER_FORMATS.length]

    return {
      id: subId,
      questionId,
      orderIndex: index,
      content: `子问题 ${index + 1}：请按要求完成「${TITLES[(questionId - 1) % TITLES.length]}」的第 ${index + 1} 步。`,
      answerFormat,
      language: answerFormat === "code" ? "bash" : undefined,
      referenceAnswer:
        answerFormat === "code"
          ? "#!/bin/bash\necho 'done'"
          : answerFormat === "image"
            ? "提交带用户名的截图"
            : "简要文字说明",
      rubric: "按步骤给分，关键步骤缺失则扣分。",
      allowSandbox: index % 3 !== 2,
      score: 10,
      createTime: baseTime,
      updateTime: baseTime,
      isDelete: 0,
    }
  })
}

function buildSeedQuestions(): Question[] {
  return TITLES.map((baseTitle, index) => {
    const id = index + 1
    const subCount = (index % 4) + 1
    const now = new Date(Date.UTC(2026, 7, 1 + (index % 28), 10, 0, 0)).toISOString()
    const subQuestions = buildSubQuestions(id, subCount, now)

    return {
      id,
      title: `${baseTitle}${index > 0 ? ` ${Math.floor(index / 3) + 1}` : ""}`.trim(),
      content: `# ${baseTitle}\n\n请阅读题目要求并完成以下子问题。\n\n![](/mock/attachments/sample-${id}.png)`,
      type: TYPES[index % TYPES.length],
      difficulty: DIFFICULTIES[index % DIFFICULTIES.length],
      tags: `Linux,${TYPES[index % TYPES.length] === "c_program" ? "C语言" : "Shell"}`,
      totalScore: subQuestions.reduce((sum, item) => sum + item.score, 0),
      submitNum: (index * 7) % 120,
      userId: 1,
      createTime: now,
      updateTime: now,
      isDelete: 0,
      subQuestions,
    }
  })
}

const questions: Question[] = buildSeedQuestions()

function delay(ms = 80): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

export async function fetchQuestions(params: {
  page: number
  title?: string
}): Promise<PaginatedQuestions> {
  await delay()

  const filtered = questions.filter(
    (question) =>
      question.isDelete === 0 &&
      (!params.title?.trim() || question.title.includes(params.title.trim()))
  )

  const total = filtered.length
  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE))
  const page = Math.min(Math.max(1, params.page), totalPages)
  const start = (page - 1) * PAGE_SIZE

  return {
    items: filtered.slice(start, start + PAGE_SIZE),
    total,
    page,
    pageSize: PAGE_SIZE,
    totalPages: total === 0 ? 1 : totalPages,
  }
}

export async function getMockQuestionById(id: number): Promise<Question | null> {
  await delay()
  const question = questions.find((item) => item.id === id && item.isDelete === 0)
  if (!question) {
    return null
  }

  return {
    ...question,
    subQuestions: [...question.subQuestions]
      .filter((item) => item.isDelete === 0)
      .sort((a, b) => a.orderIndex - b.orderIndex),
  }
}

export function questionToFormPayload(question: Question): QuestionFormPayload {
  return {
    title: question.title,
    content: question.content,
    type: question.type,
    difficulty: question.difficulty,
    tags: question.tags ?? "",
    subQuestions: question.subQuestions.map((item) => ({
      id: item.id,
      orderIndex: item.orderIndex,
      content: item.content,
      answerFormat: item.answerFormat,
      language:
        item.answerFormat === "code" && item.language?.trim()
          ? item.language.trim()
          : "",
      referenceAnswer: item.referenceAnswer ?? "",
      rubric: item.rubric ?? "",
      allowSandbox: item.allowSandbox,
      score: item.score,
    })),
  }
}

export function createEmptySubQuestion(orderIndex: number): SubQuestionFormValue {
  return {
    orderIndex,
    content: "",
    answerFormat: "text",
    language: "",
    referenceAnswer: "",
    rubric: "",
    allowSandbox: true,
    score: 10,
  }
}

export const defaultQuestionFormValues: QuestionFormPayload = {
  title: "",
  content: "",
  type: "shell_script",
  difficulty: "easy",
  tags: "",
  subQuestions: [createEmptySubQuestion(0)],
}

export function computeFormTotalScore(subQuestions: SubQuestionFormValue[]): number {
  return computeTotalScore(subQuestions)
}
