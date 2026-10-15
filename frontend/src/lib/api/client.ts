import { getLocale, i18n } from "@/i18n"
import { clearSession, readSession } from "@/lib/auth/session"

const API_BASE = "/api"

export interface ApiFieldError {
  field: string
  message: string
}

interface ErrorBody {
  message?: string
  code?: string
  fieldErrors?: ApiFieldError[]
}

export class ApiError extends Error {
  code?: string
  status?: number
  fieldErrors?: ApiFieldError[]

  constructor(
    message: string,
    code?: string,
    status?: number,
    fieldErrors?: ApiFieldError[],
  ) {
    super(message)
    this.name = "ApiError"
    this.code = code
    this.status = status
    this.fieldErrors = fieldErrors
  }
}

function isLoginPath(path: string): boolean {
  return path === "/auth/login"
}

function withLocaleHeaders(
  headers: Record<string, string>,
  init?: RequestInit,
): Record<string, string> {
  return {
    ...headers,
    "Accept-Language": getLocale(),
    ...(init?.headers as Record<string, string> | undefined),
  }
}

function shouldSkipUnauthorizedRedirect(path: string): boolean {
  return path === "/auth/login" || path === "/auth/logout"
}

async function throwApiError(path: string, response: Response): Promise<never> {
  const err = (await response.json().catch(() => ({}))) as ErrorBody
  const fallbackCodes = [
    "NOT_FOUND",
    "FORBIDDEN",
    "UNAUTHORIZED",
    "TOO_MANY_REQUESTS",
    "INVALID_PARAMETER",
    "CONFLICT",
    "FILE_TOO_LARGE",
    "STORAGE_ERROR",
    "INTERNAL_ERROR",
  ] as const
  const fallback =
    err.code && (fallbackCodes as readonly string[]).includes(err.code)
      ? i18n.t(`errors:${err.code as (typeof fallbackCodes)[number]}`)
      : i18n.t("errors:requestFailed", { status: response.status })
  const apiError = new ApiError(
    err.message || fallback,
    err.code,
    response.status,
    err.fieldErrors,
  )

  if (response.status === 401 && !shouldSkipUnauthorizedRedirect(path)) {
    clearSession()
    const { pathname, search } = window.location
    if (pathname !== "/login") {
      const redirect = encodeURIComponent(pathname + search)
      window.location.assign(`/login?redirect=${redirect}`)
    }
  }

  throw apiError
}

function parseContentDispositionFilename(header: string | null): string | null {
  if (!header) {
    return null
  }

  const utf8 = /filename\*\s*=\s*UTF-8''([^;]+)/i.exec(header)
  if (utf8?.[1]) {
    try {
      return decodeURIComponent(utf8[1].trim().replace(/^"+|"+$/g, ""))
    } catch {
      return utf8[1].trim()
    }
  }

  const quoted = /filename\s*=\s*"([^"]+)"/i.exec(header)
  if (quoted?.[1]) {
    return quoted[1]
  }

  const plain = /filename\s*=\s*([^;]+)/i.exec(header)
  if (plain?.[1]) {
    return plain[1].trim().replace(/^"+|"+$/g, "")
  }

  return null
}

export async function apiFetch<T>(path: string, init?: RequestInit): Promise<T> {
  const isFormData = init?.body instanceof FormData
  const token = isLoginPath(path) ? null : readSession()?.token

  const headers: Record<string, string> = {
    ...(isFormData ? {} : { "Content-Type": "application/json" }),
  }

  if (token) {
    headers.Authorization = `Bearer ${token}`
  }

  const response = await fetch(`${API_BASE}${path}`, {
    ...init,
    headers: withLocaleHeaders(headers, init),
  })

  if (!response.ok) {
    await throwApiError(path, response)
  }

  if (response.status === 204) {
    return undefined as T
  }

  const text = await response.text()
  if (!text) {
    return undefined as T
  }

  return JSON.parse(text) as T
}

export interface ApiDownload {
  blob: Blob
  filename: string | null
}

export async function apiDownload(path: string, init?: RequestInit): Promise<ApiDownload> {
  const token = isLoginPath(path) ? null : readSession()?.token
  const headers: Record<string, string> = {}

  if (token) {
    headers.Authorization = `Bearer ${token}`
  }

  const response = await fetch(`${API_BASE}${path}`, {
    ...init,
    headers: withLocaleHeaders(headers, init),
  })

  if (!response.ok) {
    await throwApiError(path, response)
  }

  const blob = await response.blob()
  const filename = parseContentDispositionFilename(response.headers.get("Content-Disposition"))
  return { blob, filename }
}
