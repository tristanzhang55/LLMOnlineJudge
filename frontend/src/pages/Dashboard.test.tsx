import { render, screen } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { MemoryRouter } from "react-router-dom"
import { beforeEach, describe, expect, it, vi } from "vitest"

import { ApiError } from "@/lib/api/client"
import { fetchTeacherDisputes } from "@/lib/api/teacher-disputes"
import type { AuthUser } from "@/lib/auth/types"
import { Dashboard } from "@/pages/Dashboard"

const { authState } = vi.hoisted(() => ({
  authState: {
    user: {
      id: 1,
      userAccount: "teacher01",
      userName: "王老师",
      userType: "teacher" as const,
      userRole: "user",
    } as AuthUser | null,
    token: "teacher-token" as string | null,
    login: vi.fn(),
    logout: vi.fn(),
  },
}))

vi.mock("@/lib/auth/AuthProvider", () => ({
  useAuth: () => authState,
}))

vi.mock("@/lib/api/teacher-disputes", () => ({
  fetchTeacherDisputes: vi.fn(),
}))

const mockedFetch = vi.mocked(fetchTeacherDisputes)

function pageResult(total: number) {
  return {
    items: total
      ? [
          {
            id: 99,
            submissionId: 1,
            experimentId: 1,
            experimentTitle: "should-not-render",
            classId: 1,
            studentId: 101,
            studentName: "Hidden Student",
            experimentQuestionId: 1,
            questionTitle: "Hidden Q",
            submissionStatus: "completed" as const,
            score: 10,
            disputeStatus: 1 as const,
            disputeReason: "reason",
            disputeTime: "2026-09-01T00:00:00",
            manualComment: null,
            resolveTime: null,
            isManualOverride: false,
          },
        ]
      : [],
    total,
    page: 1,
    pageSize: 1,
    totalPages: Math.max(1, total),
  }
}

function renderDashboard() {
  return render(
    <MemoryRouter>
      <Dashboard />
    </MemoryRouter>,
  )
}

describe("teacher Dashboard", () => {
  beforeEach(() => {
    mockedFetch.mockReset()
    authState.user = {
      id: 1,
      userAccount: "teacher01",
      userName: "王老师",
      userType: "teacher",
      userRole: "user",
    }
    authState.token = "teacher-token"
  })

  it("greets from the session userName and keeps the header actions", async () => {
    mockedFetch.mockResolvedValue(pageResult(3))
    renderDashboard()

    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent("欢迎回来 王老师")
    expect(screen.queryByText("张老师")).not.toBeInTheDocument()
    expect(screen.getByRole("link", { name: "+ 创建作业" })).toHaveAttribute(
      "href",
      "/teacher/assignments/new",
    )
    expect(screen.getByRole("link", { name: "+ 创建题目" })).toHaveAttribute(
      "href",
      "/teacher/questions/new",
    )
    expect(screen.getByRole("link", { name: "查看学生名单" })).toHaveAttribute(
      "href",
      "/teacher/roster",
    )
    expect(await screen.findByText("3")).toBeInTheDocument()
    expect(mockedFetch).toHaveBeenCalledWith(
      { page: 1, pageSize: 1, disputeStatus: 1 },
      expect.any(AbortSignal),
    )
  })

  it("falls back to userAccount when userName is null", async () => {
    authState.user = {
      id: 1,
      userAccount: "teacher01",
      userName: null,
      userType: "teacher",
      userRole: "user",
    }
    mockedFetch.mockResolvedValue(pageResult(0))
    renderDashboard()
    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent("欢迎回来 teacher01")
    expect(await screen.findByText("0")).toBeInTheDocument()
  })

  it("shows total only, links to /teacher/disputes, and drops the old cards", async () => {
    mockedFetch.mockResolvedValue(pageResult(4))
    renderDashboard()

    expect(await screen.findByText("4")).toBeInTheDocument()
    expect(screen.queryByText("Hidden Student")).not.toBeInTheDocument()
    expect(screen.queryByText("should-not-render")).not.toBeInTheDocument()
    expect(screen.getByRole("link", { name: /待处理申诉/ })).toHaveAttribute("href", "/teacher/disputes")
    expect(screen.queryByText("3天内截止作业")).not.toBeInTheDocument()
    expect(screen.queryByText("评分异常")).not.toBeInTheDocument()
    expect(screen.queryByText(/下一步建议/)).not.toBeInTheDocument()
  })

  it("keeps the greeting when the count fails, and offers 重试", async () => {
    const user = userEvent.setup()
    mockedFetch
      .mockRejectedValueOnce(new ApiError("Not found", "NOT_FOUND", 404))
      .mockResolvedValueOnce(pageResult(2))
    renderDashboard()

    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent("欢迎回来 王老师")
    expect(await screen.findByText("Not found")).toBeInTheDocument()
    expect(screen.getByRole("button", { name: "重试" })).toBeInTheDocument()

    await user.click(screen.getByRole("button", { name: "重试" }))
    expect(await screen.findByText("2")).toBeInTheDocument()
    expect(mockedFetch).toHaveBeenCalledTimes(2)
  })

  it("shows the 403 empty state", async () => {
    mockedFetch.mockRejectedValue(new ApiError("forbidden", "FORBIDDEN", 403))
    renderDashboard()
    expect(await screen.findByText("没有权限访问该资源")).toBeInTheDocument()
    expect(screen.getByRole("link", { name: "返回首页" })).toHaveAttribute("href", "/teacher")
    expect(screen.queryByText("待处理申诉")).not.toBeInTheDocument()
  })
})
