import type { DeepStringShape } from "@/i18n/shape"
import type { errors as Zh } from "@/i18n/locales/zh-CN/errors"

export const errors = {
  requestFailed: "Request failed ({{status}})",
  NOT_FOUND: "Not found",
  FORBIDDEN: "You don't have access to this resource",
  UNAUTHORIZED: "Not signed in or session expired",
  TOO_MANY_REQUESTS: "Too many requests. Try again later.",
  INVALID_PARAMETER: "Invalid parameter",
  CONFLICT: "Conflict",
  FILE_TOO_LARGE: "File too large",
  STORAGE_ERROR: "Storage error",
  INTERNAL_ERROR: "Internal server error",
} as const satisfies DeepStringShape<typeof Zh>
