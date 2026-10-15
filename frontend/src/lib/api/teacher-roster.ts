import { apiDownload, apiFetch } from "@/lib/api/client"
import { PAGE_SIZE } from "@/lib/mock-assignments"

export const ROSTER_TABLE_PAGE_SIZE = 50
export const ROSTER_CONFIRM_MAX_ROWS = 5000
export const ROSTER_MAX_FILE_BYTES = 5 * 1024 * 1024
export const ROSTER_TEMPLATE_FILENAME = "roster-import-template.xlsx"

export const ROSTER_FIELD_MAX = {
  studentId: 256,
  studentName: 256,
  major: 128,
  className: 255,
  semester: 64,
  initialPassword: 72,
} as const

export type RosterPreviewStatus =
  | "valid"
  | "warning"
  | "error"
  | "already_imported"

export type RosterPersistResult =
  | "created"
  | "enrolled"
  | "already_imported"
  | "rejected"

export interface RosterPreviewRow {
  rowIndex: number
  studentId: string
  studentName: string
  major: string
  className: string
  semester: string
  initialPassword: string | null
  status: RosterPreviewStatus
  messages: string[]
}

export interface RosterConfirmRow {
  rowIndex: number
  studentId: string
  studentName: string
  major: string
  className: string
  semester: string
  initialPassword: string | null
}

export interface RosterRowResult {
  rowIndex: number
  studentId: string
  result: RosterPersistResult
  userId: number | null
  classId: number | null
  messages: string[]
}

export const PREVIEW_STATUS_BADGE_VARIANT: Record<
  RosterPreviewStatus,
  "outline" | "secondary" | "destructive"
> = {
  valid: "outline",
  warning: "secondary",
  error: "destructive",
  already_imported: "outline",
}

export const PERSIST_RESULT_BADGE_VARIANT: Record<
  RosterPersistResult,
  "default" | "secondary" | "outline" | "destructive"
> = {
  created: "default",
  enrolled: "secondary",
  already_imported: "outline",
  rejected: "destructive",
}

interface ApiRosterPreviewRow {
  rowIndex?: number | null
  studentId?: string | null
  studentName?: string | null
  major?: string | null
  className?: string | null
  semester?: string | null
  initialPassword?: string | null
  status?: string | null
  messages?: string[] | null
}

interface ApiRosterPreviewResponse {
  rows?: ApiRosterPreviewRow[] | null
}

interface ApiRosterRowResult {
  rowIndex?: number | null
  studentId?: string | null
  result?: string | null
  userId?: number | null
  classId?: number | null
  messages?: string[] | null
}

interface ApiRosterConfirmResponse {
  results?: ApiRosterRowResult[] | null
}

function asString(value: string | null | undefined): string {
  return typeof value === "string" ? value : ""
}

function asMessages(value: string[] | null | undefined): string[] {
  return Array.isArray(value) ? value.filter((item) => typeof item === "string") : []
}

function asNullableId(value: number | null | undefined): number | null {
  return typeof value === "number" && Number.isFinite(value) ? value : null
}

function normalizePreviewStatus(value: string | null | undefined): RosterPreviewStatus {
  if (value === "valid" || value === "warning" || value === "error" || value === "already_imported") {
    return value
  }
  return "error"
}

function normalizePersistResult(value: string | null | undefined): RosterPersistResult {
  if (
    value === "created" ||
    value === "enrolled" ||
    value === "already_imported" ||
    value === "rejected"
  ) {
    return value
  }
  return "rejected"
}

function normalizePreviewRow(item: ApiRosterPreviewRow, index: number): RosterPreviewRow {
  const rowIndex =
    typeof item.rowIndex === "number" && item.rowIndex >= 1 ? item.rowIndex : index + 2
  const password = item.initialPassword
  return {
    rowIndex,
    studentId: asString(item.studentId),
    studentName: asString(item.studentName),
    major: asString(item.major),
    className: asString(item.className),
    semester: asString(item.semester),
    initialPassword: password == null ? null : password,
    status: normalizePreviewStatus(item.status),
    messages: asMessages(item.messages),
  }
}

function normalizeResult(item: ApiRosterRowResult, index: number): RosterRowResult {
  const rowIndex =
    typeof item.rowIndex === "number" && item.rowIndex >= 1 ? item.rowIndex : index + 1
  return {
    rowIndex,
    studentId: asString(item.studentId),
    result: normalizePersistResult(item.result),
    userId: asNullableId(item.userId),
    classId: asNullableId(item.classId),
    messages: asMessages(item.messages),
  }
}

export function toConfirmRows(previewRows: RosterPreviewRow[]): RosterConfirmRow[] {
  return previewRows.map((row) => ({
    rowIndex: row.rowIndex,
    studentId: row.studentId,
    studentName: row.studentName,
    major: row.major,
    className: row.className,
    semester: row.semester,
    initialPassword:
      row.initialPassword != null && /\S/.test(row.initialPassword) ? row.initialPassword : null,
  }))
}

export function countPreviewRows(rows: RosterPreviewRow[]): {
  total: number
  counts: Record<RosterPreviewStatus, number>
} {
  const counts: Record<RosterPreviewStatus, number> = {
    valid: 0,
    warning: 0,
    error: 0,
    already_imported: 0,
  }
  for (const row of rows) {
    counts[row.status] += 1
  }
  return { total: rows.length, counts }
}

export function countPersistResults(results: RosterRowResult[]): Record<RosterPersistResult, number> {
  const counts: Record<RosterPersistResult, number> = {
    created: 0,
    enrolled: 0,
    already_imported: 0,
    rejected: 0,
  }
  for (const item of results) {
    counts[item.result] += 1
  }
  return counts
}

function tsvCell(value: string | null): string {
  return (value ?? "").replace(/\t/g, " ").replace(/\r?\n/g, " ")
}

export function rejectedRowsToTsv(
  sentRows: RosterConfirmRow[],
  results: RosterRowResult[],
): string {
  const lines: string[] = [
    "studentId\tstudentName\tmajor\tclassName\tsemester\tinitialPassword",
  ]
  const length = Math.min(sentRows.length, results.length)
  for (let index = 0; index < length; index += 1) {
    if (results[index].result !== "rejected") {
      continue
    }
    const row = sentRows[index]
    lines.push(
      [
        tsvCell(row.studentId),
        tsvCell(row.studentName),
        tsvCell(row.major),
        tsvCell(row.className),
        tsvCell(row.semester),
        tsvCell(row.initialPassword),
      ].join("\t"),
    )
  }
  if (lines.length === 1) {
    return ""
  }
  return lines.join("\n")
}

export function isRosterXlsx(file: File): boolean {
  return file.name.toLowerCase().endsWith(".xlsx")
}

export async function downloadRosterTemplate(): Promise<void> {
  const { blob, filename } = await apiDownload("/teacher/students/import-template")
  const url = URL.createObjectURL(blob)
  const link = document.createElement("a")
  link.href = url
  link.download = filename || ROSTER_TEMPLATE_FILENAME
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}

export async function previewRosterImport(
  file: File,
  signal?: AbortSignal,
): Promise<RosterPreviewRow[]> {
  const form = new FormData()
  form.append("file", file)
  const json = await apiFetch<ApiRosterPreviewResponse>("/teacher/students/import/preview", {
    method: "POST",
    body: form,
    signal,
  })
  return (json.rows ?? []).map((item, index) => normalizePreviewRow(item, index))
}

export async function confirmRosterImport(
  rows: RosterConfirmRow[],
  signal?: AbortSignal,
): Promise<RosterRowResult[]> {
  const json = await apiFetch<ApiRosterConfirmResponse>("/teacher/students/import/confirm", {
    method: "POST",
    body: JSON.stringify({ rows }),
    signal,
  })
  return (json.results ?? []).map((item, index) => normalizeResult(item, index))
}

export async function createTeacherStudent(
  row: Omit<RosterConfirmRow, "rowIndex">,
): Promise<RosterRowResult> {
  const item = await apiFetch<ApiRosterRowResult>("/teacher/students", {
    method: "POST",
    body: JSON.stringify({
      studentId: row.studentId,
      studentName: row.studentName,
      major: row.major,
      className: row.className,
      semester: row.semester,
      initialPassword: row.initialPassword,
    }),
  })
  return normalizeResult(item, 0)
}

export const ROSTER_RESET_PASSWORD = "123456"

export interface RosterEnrollment {
  classStudentId: number
  studentId: string
  studentName: string
  major: string | null
  classId: number
  className: string
  semester: string | null
  joinTime: string
}

export interface RosterEnrollmentQuery {
  page?: number
  pageSize?: number
  classId?: number | null
  semester?: string | null
  studentName?: string
}

export interface PaginatedRosterEnrollments {
  items: RosterEnrollment[]
  total: number
  page: number
  pageSize: number
  totalPages: number
}

export interface UpdateRosterEnrollmentBody {
  studentName: string
  major: string
  classId: number
}

interface ApiRosterEnrollment {
  classStudentId?: number | null
  studentId?: string | null
  studentName?: string | null
  major?: string | null
  classId?: number | null
  className?: string | null
  semester?: string | null
  joinTime?: string | null
}

interface ApiRosterEnrollmentListResponse {
  data?: ApiRosterEnrollment[] | null
  total?: number | null
}

interface ApiRosterResetPasswordResponse {
  classStudentId?: number | null
  reset?: boolean | null
}

function asNullableText(value: string | null | undefined): string | null {
  if (typeof value !== "string") {
    return null
  }
  const trimmed = value.trim()
  return trimmed.length > 0 ? value : null
}

function normalizeEnrollment(item: ApiRosterEnrollment): RosterEnrollment | null {
  const classStudentId = asNullableId(item.classStudentId)
  const classId = asNullableId(item.classId)
  if (classStudentId == null || classStudentId < 1 || classId == null || classId < 1) {
    return null
  }
  return {
    classStudentId,
    studentId: asString(item.studentId),
    studentName: asString(item.studentName),
    major: asNullableText(item.major),
    classId,
    className: asString(item.className),
    semester: asNullableText(item.semester),
    joinTime: asString(item.joinTime),
  }
}

export async function fetchRosterEnrollments(
  params: RosterEnrollmentQuery,
  signal?: AbortSignal,
): Promise<PaginatedRosterEnrollments> {
  const page = params.page ?? 1
  const pageSize = params.pageSize ?? PAGE_SIZE
  const search = new URLSearchParams({
    page: String(page),
    pageSize: String(pageSize),
  })
  if (params.classId != null) {
    search.set("classId", String(params.classId))
  }
  if (params.semester?.trim()) {
    search.set("semester", params.semester.trim())
  }
  if (params.studentName?.trim()) {
    search.set("studentName", params.studentName.trim())
  }

  const json = await apiFetch<ApiRosterEnrollmentListResponse>(`/teacher/students?${search}`, {
    signal,
  })
  const total = json.total ?? 0
  const items = (json.data ?? [])
    .map((item) => normalizeEnrollment(item))
    .filter((item): item is RosterEnrollment => item != null)

  return {
    items,
    total,
    page,
    pageSize,
    totalPages: Math.max(1, Math.ceil(total / pageSize)),
  }
}

export async function updateRosterEnrollment(
  classStudentId: number,
  body: UpdateRosterEnrollmentBody,
): Promise<RosterEnrollment> {
  const item = await apiFetch<ApiRosterEnrollment>(`/teacher/students/${classStudentId}`, {
    method: "PUT",
    body: JSON.stringify({
      studentName: body.studentName,
      major: body.major,
      classId: body.classId,
    }),
  })
  const normalized = normalizeEnrollment(item)
  if (normalized == null) {
    return {
      classStudentId,
      studentId: "",
      studentName: body.studentName,
      major: body.major,
      classId: body.classId,
      className: "",
      semester: null,
      joinTime: "",
    }
  }
  return normalized
}

export async function deleteRosterEnrollment(classStudentId: number): Promise<void> {
  await apiFetch<void>(`/teacher/students/${classStudentId}`, { method: "DELETE" })
}

export async function resetRosterPassword(
  classStudentId: number,
): Promise<{ classStudentId: number; reset: true }> {
  const json = await apiFetch<ApiRosterResetPasswordResponse>(
    `/teacher/students/${classStudentId}/reset-password`,
    { method: "POST" },
  )
  return {
    classStudentId:
      typeof json.classStudentId === "number" && Number.isFinite(json.classStudentId)
        ? json.classStudentId
        : classStudentId,
    reset: true,
  }
}
