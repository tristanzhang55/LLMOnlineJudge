import { render, screen } from "@testing-library/react"
import { MemoryRouter } from "react-router-dom"
import { beforeEach, describe, expect, it, vi } from "vitest"

import { ApiError } from "@/lib/api/client"
import { fetchUserProfile } from "@/lib/api/user"
import { Settings } from "@/pages/Settings"

vi.mock("@/lib/auth/AuthProvider", () => ({
  useAuth: () => ({
    user: null,
    token: "teacher-token",
    login: vi.fn(),
    logout: vi.fn().mockResolvedValue(undefined),
  }),
}))

vi.mock("@/lib/api/user", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/api/user")>()
  return {
    ...actual,
    fetchUserProfile: vi.fn(),
    changePassword: vi.fn(),
  }
})

const mockedFetch = vi.mocked(fetchUserProfile)

function renderSettings() {
  return render(
    <MemoryRouter>
      <Settings />
    </MemoryRouter>,
  )
}

describe("teacher Settings page", () => {
  beforeEach(() => {
    mockedFetch.mockReset()
  })

  it("renders password then profile, with no page-level h1 and no 外观 block", async () => {
    let resolveProfile: ((value: Awaited<ReturnType<typeof fetchUserProfile>>) => void) | undefined
    mockedFetch.mockImplementation(
      () =>
        new Promise((resolve) => {
          resolveProfile = resolve
        }),
    )

    renderSettings()

    expect(screen.getByText("修改密码")).toBeInTheDocument()
    expect(screen.getByLabelText("当前密码")).toBeEnabled()
    expect(screen.getByText("个人信息")).toBeInTheDocument()
    expect(screen.getByText("加载中...")).toBeInTheDocument()
    expect(screen.queryByRole("heading", { level: 1 })).not.toBeInTheDocument()
    expect(screen.queryByText("外观")).not.toBeInTheDocument()

    resolveProfile?.({
      id: 1,
      userAccount: "teacher01",
      userName: "王老师",
      userType: "teacher",
      userRole: "user",
      major: null,
      enrollments: [],
    })
    expect(await screen.findByText("teacher01")).toBeInTheDocument()
  })

  it("shows the teacher 403 empty state linking home to /teacher", async () => {
    mockedFetch.mockRejectedValue(new ApiError("forbidden", "FORBIDDEN", 403))
    renderSettings()
    expect(await screen.findByText("没有权限访问该资源")).toBeInTheDocument()
    expect(screen.getByRole("link", { name: "返回首页" })).toHaveAttribute("href", "/teacher")
    expect(screen.queryByText("修改密码")).not.toBeInTheDocument()
  })
})
