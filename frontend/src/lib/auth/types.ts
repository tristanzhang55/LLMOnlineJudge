export interface AuthUser {
  id: number
  userAccount: string
  userName: string | null
  userType: "teacher" | "student" | null
  userRole: string
}

export interface AuthSession {
  token: string
  user: AuthUser
}
