import { render, screen } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { MemoryRouter, Route, Routes } from "react-router-dom"
import { beforeEach, describe, expect, it, vi } from "vitest"

import { ApiError } from "@/lib/api/client"
import { changePassword } from "@/lib/api/user"
import { PasswordChangeForm } from "@/pages/settings/PasswordChangeForm"

vi.mock("@/lib/api/user", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/api/user")>()
  return {
    ...actual,
    changePassword: vi.fn(),
  }
})

const { authState } = vi.hoisted(() => ({
  authState: {
    user: null,
    token: "teacher-token",
    login: vi.fn(),
    logout: vi.fn(),
  },
}))

vi.mock("@/lib/auth/AuthProvider", () => ({
  useAuth: () => authState,
}))

const mockedChangePassword = vi.mocked(changePassword)

async function fillValidNewPair(
  user: ReturnType<typeof userEvent.setup>,
  oldPassword: string,
  newPassword: string,
) {
  await user.type(screen.getByLabelText("当前密码"), oldPassword)
  await user.type(screen.getByLabelText("新密码"), newPassword)
  await user.type(screen.getByLabelText("确认新密码"), newPassword)
}

function renderForm(onForbidden = vi.fn()) {
  return render(
    <MemoryRouter initialEntries={["/teacher/settings"]}>
      <Routes>
        <Route path="/teacher/settings" element={<PasswordChangeForm onForbidden={onForbidden} />} />
        <Route path="/login" element={<div>login-page</div>} />
      </Routes>
    </MemoryRouter>,
  )
}

describe("teacher PasswordChangeForm", () => {
  beforeEach(() => {
    mockedChangePassword.mockReset()
    mockedChangePassword.mockResolvedValue(undefined)
    authState.logout.mockReset()
    authState.logout.mockResolvedValue(undefined)
  })

  it("keeps teacher recovery copy off the card", () => {
    renderForm()
    expect(screen.getByText("修改成功后需重新登录。")).toBeInTheDocument()
    expect(screen.queryByText(/联系任课教师/)).not.toBeInTheDocument()
  })

  it("does not POST on empty submit", async () => {
    const user = userEvent.setup()
    renderForm()
    await user.click(screen.getByRole("button", { name: "保存新密码" }))
    expect(screen.getByText("当前密码不能为空")).toBeInTheDocument()
    expect(screen.getByText("新密码长度需为 8-72 位")).toBeInTheDocument()
    expect(mockedChangePassword).not.toHaveBeenCalled()
  })

  it("does not POST a 7-character new password and caps input at 72", async () => {
    const user = userEvent.setup()
    renderForm()
    await fillValidNewPair(user, "oldpass1", "1234567")
    await user.click(screen.getByRole("button", { name: "保存新密码" }))
    expect(screen.getByText("新密码长度需为 8-72 位")).toBeInTheDocument()
    expect(mockedChangePassword).not.toHaveBeenCalled()
    expect(screen.getByLabelText("新密码")).toHaveAttribute("maxLength", "72")
    expect(screen.getByLabelText("确认新密码")).toHaveAttribute("maxLength", "72")
  })

  it("hangs a confirm mismatch on 确认新密码", async () => {
    const user = userEvent.setup()
    renderForm()
    await user.type(screen.getByLabelText("当前密码"), "oldpass1")
    await user.type(screen.getByLabelText("新密码"), "newpass12")
    await user.type(screen.getByLabelText("确认新密码"), "newpass13")
    await user.click(screen.getByRole("button", { name: "保存新密码" }))
    expect(screen.getByText("两次输入的新密码不一致")).toBeInTheDocument()
    expect(mockedChangePassword).not.toHaveBeenCalled()
  })

  it("rejects a new password equal to the current password client-side", async () => {
    const user = userEvent.setup()
    renderForm()
    await fillValidNewPair(user, "samepass", "samepass")
    await user.click(screen.getByRole("button", { name: "保存新密码" }))
    expect(screen.getByText("新密码不能与当前密码相同")).toBeInTheDocument()
    expect(mockedChangePassword).not.toHaveBeenCalled()
  })

  it("rejects the known default password123 client-side", async () => {
    const user = userEvent.setup()
    renderForm()
    await fillValidNewPair(user, "oldpass1", "password123")
    await user.click(screen.getByRole("button", { name: "保存新密码" }))
    expect(screen.getByText("新密码不能使用默认密码")).toBeInTheDocument()
    expect(mockedChangePassword).not.toHaveBeenCalled()
  })

  it("submits a 6-character current password (teacher-reset 123456 is allowed)", async () => {
    const user = userEvent.setup()
    renderForm()
    await fillValidNewPair(user, "123456", "newpass12")
    await user.click(screen.getByRole("button", { name: "保存新密码" }))
    expect(mockedChangePassword).toHaveBeenCalledWith("123456", "newpass12")
  })

  it("hangs a 400 wrong current password on 当前密码 and does not logout", async () => {
    const user = userEvent.setup()
    const onForbidden = vi.fn()
    mockedChangePassword.mockRejectedValue(
      new ApiError("当前密码不正确", "INVALID_PARAMETER", 400, [
        { field: "oldPassword", message: "当前密码不正确" },
      ]),
    )
    renderForm(onForbidden)
    await fillValidNewPair(user, "wrongpass", "newpass12")
    await user.click(screen.getByRole("button", { name: "保存新密码" }))
    expect(await screen.findByText("当前密码不正确")).toBeInTheDocument()
    expect(onForbidden).not.toHaveBeenCalled()
    expect(authState.logout).not.toHaveBeenCalled()
    expect(screen.getByRole("button", { name: "保存新密码" })).toBeInTheDocument()
    expect(screen.queryByText("login-page")).not.toBeInTheDocument()
  })

  it("calls logout and lands on /login after 204", async () => {
    const user = userEvent.setup()
    renderForm()
    await fillValidNewPair(user, "oldpass1", "newpass12")
    await user.click(screen.getByRole("button", { name: "保存新密码" }))
    expect(await screen.findByText("login-page")).toBeInTheDocument()
    expect(mockedChangePassword).toHaveBeenCalledTimes(1)
    expect(authState.logout).toHaveBeenCalledTimes(1)
  })

  it("toggles all three fields between password and text", async () => {
    const user = userEvent.setup()
    renderForm()
    expect(screen.getByLabelText("当前密码")).toHaveAttribute("type", "password")
    await user.click(screen.getAllByRole("button", { name: "显示密码" })[0]!)
    expect(screen.getByLabelText("当前密码")).toHaveAttribute("type", "text")
    expect(screen.getByLabelText("新密码")).toHaveAttribute("type", "text")
    expect(screen.getByLabelText("确认新密码")).toHaveAttribute("type", "text")
    await user.click(screen.getAllByRole("button", { name: "隐藏密码" })[0]!)
    expect(screen.getByLabelText("当前密码")).toHaveAttribute("type", "password")
  })
})
