import { beforeEach, describe, expect, it, vi } from "vitest"

import { apiFetch } from "@/lib/api/client"
import { changePassword, fetchUserProfile, validatePasswordChange } from "@/lib/api/user"

vi.mock("@/lib/api/client", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/api/client")>()
  return {
    ...actual,
    apiFetch: vi.fn(),
  }
})

const mockedFetch = vi.mocked(apiFetch)

describe("validatePasswordChange", () => {
  it("reports empty current password and short new password together, without confirm", () => {
    expect(validatePasswordChange({ oldPassword: "", newPassword: "", confirmPassword: "" })).toEqual({
      oldPassword: "required",
      newPassword: "length",
    })
  })

  it("rejects a 7-character new password", () => {
    expect(
      validatePasswordChange({
        oldPassword: "oldpass1",
        newPassword: "1234567",
        confirmPassword: "1234567",
      }),
    ).toEqual({ newPassword: "length" })
  })

  it("rejects a confirm mismatch only after new password is structurally valid", () => {
    expect(
      validatePasswordChange({
        oldPassword: "oldpass1",
        newPassword: "newpass12",
        confirmPassword: "newpass13",
      }),
    ).toEqual({ confirmPassword: "mismatch" })
  })

  it("rejects a new password equal to the current password before the default-password rule", () => {
    expect(
      validatePasswordChange({
        oldPassword: "password123",
        newPassword: "password123",
        confirmPassword: "password123",
      }),
    ).toEqual({ newPassword: "sameAsOld" })
  })

  it("rejects the known default password123 when current password is different", () => {
    expect(
      validatePasswordChange({
        oldPassword: "oldpass1",
        newPassword: "password123",
        confirmPassword: "password123",
      }),
    ).toEqual({ newPassword: "defaultPassword" })
  })

  it("does not apply 8-72 to oldPassword, including teacher-reset 123456", () => {
    expect(
      validatePasswordChange({
        oldPassword: "123456",
        newPassword: "newpass12",
        confirmPassword: "newpass12",
      }),
    ).toEqual({})
  })

  it("keeps leading and internal spaces (no trim)", () => {
    expect(
      validatePasswordChange({
        oldPassword: "oldpass1",
        newPassword: "        ",
        confirmPassword: "        ",
      }),
    ).toEqual({})
  })
})

describe("changePassword", () => {
  beforeEach(() => {
    mockedFetch.mockReset()
    mockedFetch.mockResolvedValue(undefined)
  })

  it("POSTs only oldPassword and newPassword, untrimmed", async () => {
    await changePassword(" old ", " newpass ")
    expect(mockedFetch).toHaveBeenCalledWith("/user/password", {
      method: "POST",
      body: JSON.stringify({ oldPassword: " old ", newPassword: " newpass " }),
    })
    const body = JSON.parse((mockedFetch.mock.calls[0]?.[1] as RequestInit).body as string) as Record<
      string,
      string
    >
    expect(body).not.toHaveProperty("confirmPassword")
  })
})

describe("fetchUserProfile", () => {
  beforeEach(() => {
    mockedFetch.mockReset()
  })

  it("GETs /user/profile with no query and keeps major/enrollments for the caller to ignore", async () => {
    mockedFetch.mockResolvedValue({
      id: 1,
      userAccount: "teacher01",
      userName: "王老师",
      userType: "teacher",
      userRole: "user",
      major: null,
      enrollments: [],
    })

    const profile = await fetchUserProfile()

    expect(mockedFetch).toHaveBeenCalledWith("/user/profile", { signal: undefined })
    expect(profile.major).toBeNull()
    expect(profile.enrollments).toEqual([])
  })
})
