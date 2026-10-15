import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useState,
  type ReactNode,
} from "react"

import { loginRequest, logoutRequest } from "@/lib/api/auth"
import { clearSession, readSession, writeSession } from "@/lib/auth/session"
import type { AuthUser } from "@/lib/auth/types"

interface AuthContextValue {
  user: AuthUser | null
  token: string | null
  login: (userAccount: string, userPassword: string) => Promise<AuthUser>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState(readSession)

  const login = useCallback(async (userAccount: string, userPassword: string) => {
    const response = await loginRequest({ userAccount, userPassword })
    const next = { token: response.token, user: response.user }
    writeSession(next)
    setSession(next)
    return response.user
  }, [])

  const logout = useCallback(async () => {
    try {
      await logoutRequest()
    } catch {
      // best-effort: expired / network / 401 still drop the tab session
    } finally {
      clearSession()
      setSession(null)
    }
  }, [])

  const value = useMemo(
    () => ({
      user: session?.user ?? null,
      token: session?.token ?? null,
      login,
      logout,
    }),
    [session, login, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error("useAuth must be used within AuthProvider")
  }
  return context
}
