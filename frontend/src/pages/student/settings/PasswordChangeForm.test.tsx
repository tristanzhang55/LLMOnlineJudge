import { render, screen } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { MemoryRouter, Route, Routes } from "react-router-dom"
import { beforeEach, describe, expect, it, vi } from "vitest"

import { ApiError } from "@/lib/api/client"
import { changePassword } from "@/lib/api/user"
import { PasswordChangeForm } from "@/pages/student/settings/PasswordChangeForm"

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
    token: "student-token",
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

function renderForm() {
  return render(
    <MemoryRouter initialEntries={["/student/settings"]}>
      <Routes>
        <Route path="/student/settings" element={<PasswordChangeForm onForbidden={vi.fn()} />} />
        <Route path="/login" element={<div>login-page</div>} />
      </Routes>
    </MemoryRouter>,
  )
}

describe("student PasswordChangeForm", () => {
  beforeEach(() => {
    mockedChangePassword.mockReset()
    mockedChangePassword.mockResolvedValue(undefined)
    authState.logout.mockReset()
    authState.logout.mockResolvedValue(undefined)
  })

  it("keeps student recovery copy on the card", () => {
    renderForm()
    expect(
      screen.getByText("修改成功后需重新登录。忘记当前密码请联系任课教师重置。"),
    ).toBeInTheDocument()
  })

  it("hangs a 400 wrong current password and does not logout", async () => {
    const user = userEvent.setup()
    mockedChangePassword.mockRejectedValue(
      new ApiError("当前密码不正确", "INVALID_PARAMETER", 400, [
        { field: "oldPassword", message: "当前密码不正确" },
      ]),
    )
    renderForm()
    await fillValidNewPair(user, "wrongpass", "newpass12")
    await user.click(screen.getByRole("button", { name: "保存新密码" }))
    expect(await screen.findByText("当前密码不正确")).toBeInTheDocument()
    expect(authState.logout).not.toHaveBeenCalled()
    expect(screen.queryByText("login-page")).not.toBeInTheDocument()
  })

  it("calls logout and lands on /login after 204", async () => {
    const user = userEvent.setup()
    renderForm()
    await fillValidNewPair(user, "oldpass1", "newpass12")
    await user.click(screen.getByRole("button", { name: "保存新密码" }))
    expect(await screen.findByText("login-page")).toBeInTheDocument()
    expect(authState.logout).toHaveBeenCalledTimes(1)
  })
})
