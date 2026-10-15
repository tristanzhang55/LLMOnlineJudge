import { apiFetch } from "@/lib/api/client"

export const NEW_PASSWORD_MIN = 8
export const NEW_PASSWORD_MAX = 72
export const REJECTED_NEW_PASSWORDS = ["123456", "password123"] as const

export interface UserEnrollment {
  classId: number
  className: string
  semester: string | null
  joinTime: string | null
}

export interface UserProfile {
  id: number
  userAccount: string
  userName: string | null
  userType: "teacher" | "student" | null
  userRole: string
  major: string | null
  enrollments: UserEnrollment[]
}

export type PasswordFormFields = {
  oldPassword: string
  newPassword: string
  confirmPassword: string
}

export type PasswordErrorCode = "required" | "length" | "mismatch" | "sameAsOld" | "defaultPassword"

export type PasswordFieldErrors = Partial<Record<keyof PasswordFormFields, string>>
export type PasswordFieldErrorCodes = Partial<Record<keyof PasswordFormFields, PasswordErrorCode>>

interface ApiUserEnrollment {
  classId?: number | null
  className?: string | null
  semester?: string | null
  joinTime?: string | null
}

interface ApiUserProfile {
  id?: number | null
  userAccount?: string | null
  userName?: string | null
  userType?: string | null
  userRole?: string | null
  major?: string | null
  enrollments?: ApiUserEnrollment[] | null
}

function asBlankToNull(value: string | null | undefined): string | null {
  if (typeof value !== "string") {
    return null
  }
  return value.trim() ? value : null
}

function normalizeEnrollment(row: ApiUserEnrollment): UserEnrollment | null {
  if (typeof row.classId !== "number" || !Number.isFinite(row.classId)) {
    return null
  }
  return {
    classId: row.classId,
    className: asBlankToNull(row.className) ?? "",
    semester: asBlankToNull(row.semester),
    joinTime: asBlankToNull(row.joinTime),
  }
}

function normalizeUserProfile(payload: ApiUserProfile | null | undefined): UserProfile {
  if (typeof payload?.id !== "number" || !Number.isFinite(payload.id)) {
    throw new Error("Invalid user profile")
  }

  const userType = payload.userType
  const enrollments = Array.isArray(payload.enrollments)
    ? payload.enrollments
        .map(normalizeEnrollment)
        .filter((row): row is UserEnrollment => row != null)
    : []

  return {
    id: payload.id,
    userAccount: typeof payload.userAccount === "string" ? payload.userAccount : "",
    userName:
      typeof payload.userName === "string" && payload.userName !== ""
        ? payload.userName
        : null,
    userType: userType === "teacher" || userType === "student" ? userType : null,
    userRole: typeof payload.userRole === "string" ? payload.userRole : "",
    major: asBlankToNull(payload.major),
    enrollments,
  }
}

export function validatePasswordChange(fields: PasswordFormFields): PasswordFieldErrorCodes {
  const errors: PasswordFieldErrorCodes = {}

  if (fields.oldPassword.length === 0) {
    errors.oldPassword = "required"
  }

  if (
    fields.newPassword.length === 0 ||
    fields.newPassword.length < NEW_PASSWORD_MIN ||
    fields.newPassword.length > NEW_PASSWORD_MAX
  ) {
    errors.newPassword = "length"
  }

  if (!errors.newPassword && fields.confirmPassword !== fields.newPassword) {
    errors.confirmPassword = "mismatch"
  }

  if (!errors.newPassword && fields.newPassword === fields.oldPassword) {
    errors.newPassword = "sameAsOld"
  }

  if (
    !errors.newPassword &&
    (REJECTED_NEW_PASSWORDS as readonly string[]).includes(fields.newPassword)
  ) {
    errors.newPassword = "defaultPassword"
  }

  return errors
}

export async function fetchUserProfile(signal?: AbortSignal): Promise<UserProfile> {
  const body = await apiFetch<ApiUserProfile>("/user/profile", { signal })
  return normalizeUserProfile(body)
}

export async function changePassword(oldPassword: string, newPassword: string): Promise<void> {
  await apiFetch<void>("/user/password", {
    method: "POST",
    body: JSON.stringify({ oldPassword, newPassword }),
  })
}
