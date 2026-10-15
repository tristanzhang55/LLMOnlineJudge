import { render, screen, waitFor } from "@testing-library/react"
import { beforeEach, describe, expect, it, vi } from "vitest"

import { ApiError } from "@/lib/api/client"
import { fetchUserProfile } from "@/lib/api/user"
import { ProfileCard } from "@/pages/settings/ProfileCard"

vi.mock("@/lib/api/user", () => ({
  fetchUserProfile: vi.fn(),
}))

const mockedFetch = vi.mocked(fetchUserProfile)

describe("teacher ProfileCard", () => {
  beforeEach(() => {
    mockedFetch.mockReset()
  })

  it("renders 账号 and 姓名 from the profile payload, never 学号/专业/我的班级", async () => {
    mockedFetch.mockResolvedValue({
      id: 1,
      userAccount: "teacher01",
      userName: "王老师",
      userType: "teacher",
      userRole: "user",
      major: "计算机",
      enrollments: [
        {
          classId: 9,
          className: "操作系统A班",
          semester: "2026春",
          joinTime: "2026-03-01T08:00:00",
        },
      ],
    })

    render(<ProfileCard onForbidden={vi.fn()} />)

    expect(await screen.findByText("teacher01")).toBeInTheDocument()
    expect(screen.getByText("王老师")).toBeInTheDocument()
    expect(screen.getByText("账号")).toBeInTheDocument()
    expect(screen.getByText("姓名")).toBeInTheDocument()
    expect(screen.queryByText("学号")).not.toBeInTheDocument()
    expect(screen.queryByText("专业")).not.toBeInTheDocument()
    expect(screen.queryByText("我的班级")).not.toBeInTheDocument()
    expect(screen.queryByText("操作系统A班")).not.toBeInTheDocument()
    expect(screen.queryByText("计算机")).not.toBeInTheDocument()
    expect(screen.queryByText(/联系任课教师/)).not.toBeInTheDocument()
    expect(screen.queryByRole("textbox")).not.toBeInTheDocument()
  })

  it("shows a retry on a non-403 load error", async () => {
    mockedFetch.mockRejectedValue(new ApiError("Not found", "NOT_FOUND", 404))
    render(<ProfileCard onForbidden={vi.fn()} />)
    expect(await screen.findByText("Not found")).toBeInTheDocument()
    expect(screen.getByRole("button", { name: "重试" })).toBeInTheDocument()
  })

  it("lifts a 403 to the page instead of hanging the card", async () => {
    const onForbidden = vi.fn()
    mockedFetch.mockRejectedValue(new ApiError("forbidden", "FORBIDDEN", 403))
    render(<ProfileCard onForbidden={onForbidden} />)
    await waitFor(() => expect(onForbidden).toHaveBeenCalled())
  })
})
