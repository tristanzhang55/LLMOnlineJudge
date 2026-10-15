import { apiFetch } from "@/lib/api/client"
import type { AuthUser } from "@/lib/auth/types"

export interface LoginRequest {
  userAccount: string
  userPassword: string
}

export interface LoginResponse {
  token: string
  user: AuthUser
}

export function loginRequest(body: LoginRequest): Promise<LoginResponse> {
  return apiFetch<LoginResponse>("/auth/login", {
    method: "POST",
    body: JSON.stringify(body),
  })
}

export async function logoutRequest(): Promise<void> {
  await apiFetch<void>("/auth/logout", { method: "POST" })
}
