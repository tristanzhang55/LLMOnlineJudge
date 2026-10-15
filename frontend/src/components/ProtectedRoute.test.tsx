import { render, screen } from "@testing-library/react"
import { MemoryRouter, Route, Routes } from "react-router-dom"
import { describe, expect, it, vi } from "vitest"

import { ProtectedRoute } from "@/components/ProtectedRoute"
import type { AuthUser } from "@/lib/auth/types"

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

function renderTeacherSettingsGate() {
  return render(
    <MemoryRouter initialEntries={["/teacher/settings"]}>
      <Routes>
        <Route
          path="/teacher/settings"
          element={
            <ProtectedRoute requiredUserType="teacher">
              <div>teacher-settings</div>
            </ProtectedRoute>
          }
        />
        <Route path="/student" element={<div>student-home</div>} />
        <Route path="/teacher" element={<div>teacher-home</div>} />
        <Route path="/login" element={<div>login</div>} />
      </Routes>
    </MemoryRouter>,
  )
}

describe("ProtectedRoute on /teacher/settings", () => {
  it("does not let a student JWT reach teacher settings", () => {
    authState.token = "student-token"
    authState.user = {
      id: 101,
      userAccount: "student01",
      userName: "李同学",
      userType: "student",
      userRole: "user",
    }
    renderTeacherSettingsGate()
    expect(screen.getByText("student-home")).toBeInTheDocument()
    expect(screen.queryByText("teacher-settings")).not.toBeInTheDocument()
  })

  it("lets a teacher JWT through", () => {
    authState.token = "teacher-token"
    authState.user = {
      id: 1,
      userAccount: "teacher01",
      userName: "王老师",
      userType: "teacher",
      userRole: "user",
    }
    renderTeacherSettingsGate()
    expect(screen.getByText("teacher-settings")).toBeInTheDocument()
  })
})
