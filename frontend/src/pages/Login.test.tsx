import { render, screen } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { MemoryRouter, Route, Routes } from "react-router-dom"
import { beforeEach, describe, expect, it, vi } from "vitest"

import { ApiError } from "@/lib/api/client"
import type { AuthUser } from "@/lib/auth/types"
import { Login } from "@/pages/Login"
import { setLocale } from "@/i18n"

const { authState } = vi.hoisted(() => ({
  authState: {
    user: null as AuthUser | null,
    token: null as string | null,
    login: vi.fn(),
    logout: vi.fn(),
  },
}))

vi.mock("@/lib/auth/AuthProvider", () => ({
  useAuth: () => authState,
}))

function renderLogin() {
  return render(
    <MemoryRouter initialEntries={["/login"]}>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/teacher" element={<div>teacher-home</div>} />
      </Routes>
    </MemoryRouter>,
  )
}

describe("Login", () => {
  beforeEach(() => {
    authState.user = null
    authState.token = null
    authState.login.mockReset()
    authState.logout.mockReset()
    authState.logout.mockResolvedValue(undefined)
  })

  it("has no 开发快捷登录 and no seed-account buttons", () => {
    renderLogin()
    expect(screen.queryByText("开发快捷登录")).not.toBeInTheDocument()
    expect(screen.queryByRole("button", { name: "teacher01" })).not.toBeInTheDocument()
    expect(screen.queryByRole("button", { name: "student01" })).not.toBeInTheDocument()
    expect(screen.getByText("忘记密码请联系任课教师重置。")).toBeInTheDocument()
  })

  it("shows 401 message inline and stays on /login", async () => {
    const user = userEvent.setup()
    authState.login.mockRejectedValue(new ApiError("账号或密码错误", "UNAUTHORIZED", 401))
    renderLogin()
    await user.type(screen.getByLabelText("账号"), "teacher01")
    await user.type(screen.getByLabelText("密码"), "wrong")
    await user.click(screen.getByRole("button", { name: "登录" }))
    expect(await screen.findByRole("alert")).toHaveTextContent("账号或密码错误")
    expect(screen.getByText("教师与学生共用登录入口")).toBeInTheDocument()
    expect(screen.queryByText("teacher-home")).not.toBeInTheDocument()
  })

  it("shows 429 message inline and does not redirect", async () => {
    const user = userEvent.setup()
    authState.login.mockRejectedValue(
      new ApiError("登录失败次数过多，请稍后再试", "TOO_MANY_REQUESTS", 429),
    )
    renderLogin()
    await user.type(screen.getByLabelText("账号"), "teacher01")
    await user.type(screen.getByLabelText("密码"), "wrong")
    await user.click(screen.getByRole("button", { name: "登录" }))
    expect(await screen.findByRole("alert")).toHaveTextContent("登录失败次数过多，请稍后再试")
    expect(screen.queryByText("teacher-home")).not.toBeInTheDocument()
  })

  it("renders the glossary Sign in label under en-US", () => {
    setLocale("en-US")
    renderLogin()
    expect(screen.getByRole("button", { name: "Sign in" })).toBeInTheDocument()
  })
})
