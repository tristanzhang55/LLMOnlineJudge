import { type ClassOption } from "@/lib/api/classes"
import { apiDownload, apiFetch } from "@/lib/api/client"
import { fetchExperiments, type ExperimentListItem } from "@/lib/api/experiments"
import { fetchRosterEnrollments } from "@/lib/api/teacher-roster"
import { PAGE_SIZE } from "@/lib/mock-assignments"

export const GRADES_EXPORT_FALLBACK_FILENAME = "grades.xlsx"
export const SEMESTER_NONE = "__none__"
export const EXPERIMENT_PICKER_PAGE_SIZE = 100

export type GradeRoundStatus =
  | "pending"
  | "grading_in_progress"
  | "completed"
  | "grading_failed"

export interface GradeStudentRow {
  studentId: string
  studentName: string
  className: string
  major: string | null
  score: number | null
  status: string | null
}

export interface GradeQuery {
  classId: number
  experimentId?: number | null
  page?: number
  pageSize?: number
  studentName?: string
}

export interface PaginatedGradeList {
  items: GradeStudentRow[]
  total: number
  page: number
  pageSize: number
  totalPages: number
  experimentSelected: boolean
}

interface ApiGradeStudentRow {
  studentId?: string | null
  studentName?: string | null
  className?: string | null
  major?: string | null
  score?: number | null
  status?: string | null
}

interface ApiGradeListResponse {
  data?: ApiGradeStudentRow[] | null
  total?: number | null
  experimentSelected?: boolean | null
}

type RecencyItem = { id: number; createTime: string | null }

function asString(value: string | null | undefined): string {
  return typeof value === "string" ? value : ""
}

function asNullableText(value: string | null | undefined): string | null {
  if (typeof value !== "string") {
    return null
  }
  const trimmed = value.trim()
  return trimmed.length > 0 ? value : null
}

function asScore(value: number | null | undefined): number | null {
  if (value == null) {
    return null
  }
  return typeof value === "number" && Number.isFinite(value) ? value : null
}

function asStatus(value: string | null | undefined): string | null {
  if (typeof value !== "string") {
    return null
  }
  const trimmed = value.trim()
  return trimmed.length > 0 ? trimmed : null
}

function recencyTime(createTime: string | null | undefined): number {
  if (!createTime) {
    return 0
  }
  const parsed = Date.parse(createTime)
  return Number.isFinite(parsed) ? parsed : 0
}

export function isNewer(a: RecencyItem, b: RecencyItem): boolean {
  const timeA = recencyTime(a.createTime)
  const timeB = recencyTime(b.createTime)
  if (timeA !== timeB) {
    return timeA > timeB
  }
  return a.id > b.id
}

export function pickLatest<T extends RecencyItem>(items: T[]): T | null {
  if (items.length === 0) {
    return null
  }
  return items.reduce((best, current) => (isNewer(current, best) ? current : best))
}

export function semesterKeyOf(item: ClassOption): string {
  const semester = item.semester?.trim()
  return semester ? semester : SEMESTER_NONE
}

export function classesInSemester(classes: ClassOption[], semesterKey: string): ClassOption[] {
  return classes.filter((item) => semesterKeyOf(item) === semesterKey)
}

export function sortByRecency<T extends RecencyItem>(items: T[]): T[] {
  return [...items].sort((a, b) => {
    if (isNewer(a, b)) {
      return -1
    }
    if (isNewer(b, a)) {
      return 1
    }
    return 0
  })
}

export function semesterOptions(classes: ClassOption[]): { key: string }[] {
  const buckets = new Map<string, RecencyItem>()
  for (const item of classes) {
    const key = semesterKeyOf(item)
    const current = { id: item.id, createTime: item.createTime }
    const existing = buckets.get(key)
    if (!existing || isNewer(current, existing)) {
      buckets.set(key, current)
    }
  }
  return [...buckets.entries()]
    .sort((a, b) => {
      if (isNewer(a[1], b[1])) {
        return -1
      }
      if (isNewer(b[1], a[1])) {
        return 1
      }
      return 0
    })
    .map(([key]) => ({ key }))
}

export function pickDefaultClass(classes: ClassOption[]): ClassOption | null {
  const overall = pickLatest(classes)
  if (!overall) {
    return null
  }
  return pickLatest(classesInSemester(classes, semesterKeyOf(overall)))
}

export function pickDefaultExperiment(items: ExperimentListItem[]): ExperimentListItem | null {
  if (items.length === 0) {
    return null
  }
  const nonDraft = items.filter((item) => item.status !== 0)
  const pool = nonDraft.length > 0 ? nonDraft : items
  return pickLatest(pool)
}

function normalizeGradeRow(item: ApiGradeStudentRow): GradeStudentRow | null {
  const studentId = asString(item.studentId)
  if (!studentId) {
    return null
  }
  return {
    studentId,
    studentName: asString(item.studentName),
    className: asString(item.className),
    major: asNullableText(item.major),
    score: asScore(item.score),
    status: asStatus(item.status),
  }
}

export async function fetchTeacherGrades(
  params: GradeQuery,
  signal?: AbortSignal,
): Promise<PaginatedGradeList> {
  const page = params.page ?? 1
  const pageSize = params.pageSize ?? PAGE_SIZE
  const search = new URLSearchParams({
    classId: String(params.classId),
    page: String(page),
    pageSize: String(pageSize),
  })
  if (params.experimentId != null && params.experimentId > 0) {
    search.set("experimentId", String(params.experimentId))
  }
  if (params.studentName?.trim()) {
    search.set("studentName", params.studentName.trim())
  }

  const json = await apiFetch<ApiGradeListResponse>(`/teacher/grades?${search}`, { signal })
  const total = json.total ?? 0
  const items = (json.data ?? [])
    .map((item) => normalizeGradeRow(item))
    .filter((item): item is GradeStudentRow => item != null)

  return {
    items,
    total,
    page,
    pageSize,
    totalPages: Math.max(1, Math.ceil(total / pageSize)),
    experimentSelected:
      json.experimentSelected ??
      Boolean(params.experimentId != null && params.experimentId > 0),
  }
}

export async function fetchExperimentsForClass(
  classId: number,
  signal?: AbortSignal,
): Promise<ExperimentListItem[]> {
  const first = await fetchExperiments(
    { classId, page: 1, pageSize: EXPERIMENT_PICKER_PAGE_SIZE },
    signal,
  )
  const items = [...first.items]
  let page = 2
  while (items.length < first.total) {
    if (signal?.aborted) {
      break
    }
    const next = await fetchExperiments(
      { classId, page, pageSize: EXPERIMENT_PICKER_PAGE_SIZE },
      signal,
    )
    if (next.items.length === 0) {
      break
    }
    items.push(...next.items)
    page += 1
  }
  return items
}

const ROSTER_MAJOR_PAGE_SIZE = 100

export async function fetchClassMajorByStudentId(
  classId: number,
  signal?: AbortSignal,
): Promise<Map<string, string | null>> {
  const majors = new Map<string, string | null>()
  let page = 1
  let total = Number.POSITIVE_INFINITY
  while (majors.size < total) {
    if (signal?.aborted) {
      break
    }
    const result = await fetchRosterEnrollments(
      { classId, page, pageSize: ROSTER_MAJOR_PAGE_SIZE },
      signal,
    )
    total = result.total
    if (result.items.length === 0) {
      break
    }
    for (const item of result.items) {
      majors.set(item.studentId, item.major)
    }
    page += 1
  }
  return majors
}

export async function downloadGradeExport(classId: number, experimentId: number): Promise<void> {
  const search = new URLSearchParams({
    classId: String(classId),
    experimentId: String(experimentId),
  })
  const { blob, filename } = await apiDownload(`/teacher/grades/export?${search}`)
  const url = URL.createObjectURL(blob)
  const link = document.createElement("a")
  link.href = url
  link.download = filename || GRADES_EXPORT_FALLBACK_FILENAME
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}

export function isGradeRoundStatus(value: string): value is GradeRoundStatus {
  return (
    value === "pending" ||
    value === "grading_in_progress" ||
    value === "completed" ||
    value === "grading_failed"
  )
}
