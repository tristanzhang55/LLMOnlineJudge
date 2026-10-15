import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"

import { ApiError, apiDownload, apiFetch } from "@/lib/api/client"
import { AUTH_STORAGE_KEY, writeSession } from "@/lib/auth/session"
import type { AuthUser } from "@/lib/auth/types"

const teacher: AuthUser = {
  id: 1,
  userAccount: "teacher01",
  userName: "王老师",
  userType: "teacher",
  userRole: "user",
}

function jsonResponse(status: number, body: unknown) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  })
}

describe("apiFetch interceptor", () => {
  const assign = vi.fn()

  beforeEach(() => {
    sessionStorage.clear()
    assign.mockReset()
    vi.stubGlobal(
      "location",
      {
        pathname: "/teacher",
        search: "",
        assign,
      } satisfies Pick<Location, "pathname" | "search" | "assign">,
    )
    vi.stubGlobal("fetch", vi.fn())
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it("does not clear or redirect on login 401", async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse(401, { message: "账号或密码错误", code: "UNAUTHORIZED" }))
    await expect(
      apiFetch("/auth/login", { method: "POST", body: JSON.stringify({}) }),
    ).rejects.toMatchObject({ status: 401, message: "账号或密码错误" } satisfies Partial<ApiError>)
    expect(assign).not.toHaveBeenCalled()
    expect(sessionStorage.getItem(AUTH_STORAGE_KEY)).toBeNull()
  })

  it("does not clear or redirect on login 429", async () => {
    vi.mocked(fetch).mockResolvedValue(
      jsonResponse(429, { message: "登录失败次数过多，请稍后再试", code: "TOO_MANY_REQUESTS" }),
    )
    await expect(
      apiFetch("/auth/login", { method: "POST", body: JSON.stringify({}) }),
    ).rejects.toMatchObject({
      status: 429,
      message: "登录失败次数过多，请稍后再试",
    })
    expect(assign).not.toHaveBeenCalled()
  })

  it("does not redirect on logout 401 and leaves session for the caller", async () => {
    writeSession({ token: "old-token", user: teacher })
    vi.mocked(fetch).mockResolvedValue(jsonResponse(401, { message: "未登录或登录已过期", code: "UNAUTHORIZED" }))
    await expect(apiFetch("/auth/logout", { method: "POST" })).rejects.toBeInstanceOf(ApiError)
    expect(assign).not.toHaveBeenCalled()
    expect(sessionStorage.getItem(AUTH_STORAGE_KEY)).not.toBeNull()
  })

  it("never treats 429 as session expiry on authenticated paths", async () => {
    writeSession({ token: "old-token", user: teacher })
    vi.mocked(fetch).mockResolvedValue(
      jsonResponse(429, { message: "登录失败次数过多，请稍后再试", code: "TOO_MANY_REQUESTS" }),
    )
    await expect(apiFetch("/classes")).rejects.toMatchObject({ status: 429 })
    expect(assign).not.toHaveBeenCalled()
    expect(sessionStorage.getItem(AUTH_STORAGE_KEY)).not.toBeNull()
  })

  it("sends Accept-Language from the current locale on json and downloads", async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse(200, {}))
    await apiFetch("/classes")
    expect(vi.mocked(fetch).mock.calls[0][1]).toEqual(
      expect.objectContaining({
        headers: expect.objectContaining({ "Accept-Language": "zh-CN" }),
      }),
    )

    vi.mocked(fetch).mockResolvedValue({
      ok: true,
      status: 200,
      blob: async () => new Blob(["xlsx"]),
      headers: {
        get: (name: string) =>
          name === "Content-Disposition"
            ? 'attachment; filename="grades.xlsx"'
            : null,
      },
    } as Response)
    await apiDownload("/teacher/grades/export")
    expect(vi.mocked(fetch).mock.calls[1][1]).toEqual(
      expect.objectContaining({
        headers: expect.objectContaining({ "Accept-Language": "zh-CN" }),
      }),
    )
  })

  it("clears and redirects on 401 for other paths", async () => {
    writeSession({ token: "old-token", user: teacher })
    vi.mocked(fetch).mockResolvedValue(jsonResponse(401, { message: "未登录或登录已过期", code: "UNAUTHORIZED" }))
    await expect(apiFetch("/classes")).rejects.toBeInstanceOf(ApiError)
    expect(sessionStorage.getItem(AUTH_STORAGE_KEY)).toBeNull()
    expect(assign).toHaveBeenCalledWith("/login?redirect=%2Fteacher")
  })
})
