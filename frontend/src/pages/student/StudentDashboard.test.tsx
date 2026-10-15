import { render, screen } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { MemoryRouter } from "react-router-dom"
import { beforeEach, describe, expect, it, vi } from "vitest"

import { ApiError } from "@/lib/api/client"
import { fetchStudentDashboardSummary } from "@/lib/api/student-dashboard"
import { fetchUserProfile } from "@/lib/api/user"
import type { AuthUser } from "@/lib/auth/types"
import { StudentDashboard } from "@/pages/student/StudentDashboard"

const { authState } = vi.hoisted(() => ({
  authState: {
    user: {
      id: 101,
      userAccount: "student01",
      userName: "学生01",
      userType: "student" as const,
      userRole: "user",
    } as AuthUser | null,
    token: "student-token" as string | null,
    login: vi.fn(),
    logout: vi.fn(),
  },
}))

vi.mock("@/lib/auth/AuthProvider", () => ({
  useAuth: () => authState,
}))

vi.mock("@/lib/api/student-dashboard", () => ({
  fetchStudentDashboardSummary: vi.fn(),
}))

vi.mock("@/lib/api/user", () => ({
  fetchUserProfile: vi.fn(),
}))

const mockedFetch = vi.mocked(fetchStudentDashboardSummary)
const mockedProfile = vi.mocked(fetchUserProfile)

function renderDashboard() {
  return render(
    <MemoryRouter>
      <StudentDashboard />
    </MemoryRouter>,
  )
}

describe("student StudentDashboard", () => {
  beforeEach(() => {
    mockedFetch.mockReset()
    mockedProfile.mockReset()
    authState.user = {
      id: 101,
      userAccount: "student01",
      userName: "学生01",
      userType: "student",
      userRole: "user",
    }
    authState.token = "student-token"
  })

  it("greets from the session userName and loads both counts from one summary GET", async () => {
    mockedFetch.mockResolvedValue({ pendingAssignments: 2, disputesWithNewReply: 1 })
    renderDashboard()

    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent("欢迎回来 学生01")
    expect(screen.queryByText("李同学")).not.toBeInTheDocument()
    expect(await screen.findByText("2")).toBeInTheDocument()
    expect(screen.getByText("1")).toBeInTheDocument()
    expect(mockedFetch).toHaveBeenCalledTimes(1)
    expect(mockedFetch).toHaveBeenCalledWith(expect.any(AbortSignal))
    expect(mockedProfile).not.toHaveBeenCalled()
  })

  it("falls back to userAccount when userName is null, without suffixing 同学", async () => {
    authState.user = {
      id: 101,
      userAccount: "student01",
      userName: null,
      userType: "student",
      userRole: "user",
    }
    mockedFetch.mockResolvedValue({ pendingAssignments: 0, disputesWithNewReply: 0 })
    renderDashboard()
    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent("欢迎回来 student01")
    expect(screen.getByRole("heading", { level: 1 })).not.toHaveTextContent("同学")
    expect(await screen.findAllByText("0")).toHaveLength(2)
  })

  it("links to the list pages without status queries and drops the old cards", async () => {
    mockedFetch.mockResolvedValue({ pendingAssignments: 3, disputesWithNewReply: 4 })
    renderDashboard()
    expect(await screen.findByText("3")).toBeInTheDocument()

    expect(screen.getByRole("link", { name: /待提交作业/ })).toHaveAttribute("href", "/student/assignments")
    expect(screen.getByRole("link", { name: /申诉有新回复/ })).toHaveAttribute("href", "/student/disputes")
    expect(screen.getByRole("link", { name: /待提交作业/ }).getAttribute("href")).not.toContain("status")
    expect(screen.getByRole("link", { name: /申诉有新回复/ }).getAttribute("href")).not.toContain("status")

    expect(screen.queryByText("待批改中")).not.toBeInTheDocument()
    expect(screen.queryByText(/下一步建议/)).not.toBeInTheDocument()
    expect(screen.queryByText("暂无紧急待办，继续保持！")).not.toBeInTheDocument()
    expect(screen.queryByText("Shell 脚本练习")).not.toBeInTheDocument()
    expect(screen.queryByText("截止日期临近且尚未提交")).not.toBeInTheDocument()
  })

  it("keeps the greeting when the summary fails, and offers 重试", async () => {
    const user = userEvent.setup()
    mockedFetch
      .mockRejectedValueOnce(new ApiError("Not found", "NOT_FOUND", 404))
      .mockResolvedValueOnce({ pendingAssignments: 5, disputesWithNewReply: 0 })
    renderDashboard()

    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent("欢迎回来 学生01")
    expect(await screen.findByText("Not found")).toBeInTheDocument()
    expect(screen.queryByText("5")).not.toBeInTheDocument()

    await user.click(screen.getByRole("button", { name: "重试" }))
    expect(await screen.findByText("5")).toBeInTheDocument()
    expect(mockedFetch).toHaveBeenCalledTimes(2)
  })

  it("shows the 403 empty state", async () => {
    mockedFetch.mockRejectedValue(new ApiError("forbidden", "FORBIDDEN", 403))
    renderDashboard()
    expect(await screen.findByText("没有权限访问该资源")).toBeInTheDocument()
    expect(screen.getByRole("link", { name: "返回首页" })).toHaveAttribute("href", "/student")
    expect(screen.queryByText("待提交作业")).not.toBeInTheDocument()
  })
})
