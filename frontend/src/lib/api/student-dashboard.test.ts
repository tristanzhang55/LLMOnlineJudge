import { beforeEach, describe, expect, it, vi } from "vitest"

import { apiFetch } from "@/lib/api/client"
import { fetchStudentDashboardSummary } from "@/lib/api/student-dashboard"

vi.mock("@/lib/api/client", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/api/client")>()
  return {
    ...actual,
    apiFetch: vi.fn(),
  }
})

const mockedFetch = vi.mocked(apiFetch)

describe("fetchStudentDashboardSummary", () => {
  beforeEach(() => {
    mockedFetch.mockReset()
  })

  it("GETs /student/dashboard/summary with no query and no userName field", async () => {
    mockedFetch.mockResolvedValue({
      pendingAssignments: 2,
      disputesWithNewReply: 1,
      userName: "should-ignore",
      gradingInProgress: 9,
    })

    const summary = await fetchStudentDashboardSummary()

    expect(mockedFetch).toHaveBeenCalledWith("/student/dashboard/summary", { signal: undefined })
    expect(summary).toEqual({ pendingAssignments: 2, disputesWithNewReply: 1 })
    expect(summary).not.toHaveProperty("userName")
    expect(summary).not.toHaveProperty("gradingInProgress")
  })

  it("normalizes missing or non-finite counts to 0", async () => {
    mockedFetch.mockResolvedValue({ pendingAssignments: null })
    expect(await fetchStudentDashboardSummary()).toEqual({
      pendingAssignments: 0,
      disputesWithNewReply: 0,
    })
  })
})
