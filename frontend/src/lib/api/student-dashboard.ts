import { apiFetch } from "@/lib/api/client"

export interface StudentDashboardSummary {
  pendingAssignments: number
  disputesWithNewReply: number
}

interface ApiStudentDashboardSummary {
  pendingAssignments?: number | null
  disputesWithNewReply?: number | null
}

function asCount(value: number | null | undefined): number {
  return typeof value === "number" && Number.isFinite(value) ? value : 0
}

function normalizeSummary(payload: ApiStudentDashboardSummary | null | undefined): StudentDashboardSummary {
  return {
    pendingAssignments: asCount(payload?.pendingAssignments),
    disputesWithNewReply: asCount(payload?.disputesWithNewReply),
  }
}

export async function fetchStudentDashboardSummary(
  signal?: AbortSignal,
): Promise<StudentDashboardSummary> {
  const body = await apiFetch<ApiStudentDashboardSummary>("/student/dashboard/summary", { signal })
  return normalizeSummary(body)
}
