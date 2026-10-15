import type { AuthUser } from "@/lib/auth/types"

export function resolvePostLoginPath(
  userType: AuthUser["userType"],
  redirectParam: string | null,
): string | null {
  if (userType !== "teacher" && userType !== "student") {
    return null
  }

  const portalPrefix = userType === "teacher" ? "/teacher" : "/student"

  if (
    redirectParam &&
    redirectParam.startsWith(portalPrefix) &&
    !redirectParam.startsWith("//") &&
    !redirectParam.includes("://")
  ) {
    return redirectParam
  }

  return portalPrefix
}
