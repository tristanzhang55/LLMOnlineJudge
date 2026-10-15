import { render, screen } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"

import { AuthProvider, useAuth } from "@/lib/auth/AuthProvider"
import { AUTH_STORAGE_KEY, writeSession } from "@/lib/auth/session"
import type { AuthUser } from "@/lib/auth/types"

const teacher: AuthUser = {
  id: 1,
  userAccount: "teacher01",
  userName: "王老师",
  userType: "teacher",
  userRole: "user",
}

function LogoutProbe() {
  const { user, logout } = useAuth()
  return (
    <div>
      <span>{user ? "signed-in" : "signed-out"}</span>
      <button type="button" onClick={() => void logout()}>
        退出
      </button>
    </div>
  )
}

describe("AuthProvider.logout", () => {
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
    writeSession({ token: "old-token", user: teacher })
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it("POSTs /auth/logout then clears auth", async () => {
    vi.mocked(fetch).mockResolvedValue(new Response(null, { status: 204 }))
    const user = userEvent.setup()
    render(
      <AuthProvider>
        <LogoutProbe />
      </AuthProvider>,
    )
    expect(screen.getByText("signed-in")).toBeInTheDocument()
    await user.click(screen.getByRole("button", { name: "退出" }))
    expect(await screen.findByText("signed-out")).toBeInTheDocument()
    expect(sessionStorage.getItem(AUTH_STORAGE_KEY)).toBeNull()
    expect(fetch).toHaveBeenCalledWith(
      "/api/auth/logout",
      expect.objectContaining({
        method: "POST",
      }),
    )
    expect(assign).not.toHaveBeenCalled()
  })

  it("clears auth even when logout returns 401, without interceptor redirect", async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response(JSON.stringify({ message: "未登录或登录已过期", code: "UNAUTHORIZED" }), {
        status: 401,
        headers: { "Content-Type": "application/json" },
      }),
    )
    const user = userEvent.setup()
    render(
      <AuthProvider>
        <LogoutProbe />
      </AuthProvider>,
    )
    await user.click(screen.getByRole("button", { name: "退出" }))
    expect(await screen.findByText("signed-out")).toBeInTheDocument()
    expect(sessionStorage.getItem(AUTH_STORAGE_KEY)).toBeNull()
    expect(assign).not.toHaveBeenCalled()
  })
})

describe("AuthUser", () => {
  it("does not include tokenVersion", () => {
    const user: AuthUser = {
      id: 1,
      userAccount: "teacher01",
      userName: null,
      userType: "teacher",
      userRole: "user",
    }
    expect(user).not.toHaveProperty("tokenVersion")
  })
})
