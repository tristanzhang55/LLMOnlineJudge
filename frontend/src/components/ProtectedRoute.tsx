import type { ReactNode } from "react"
import { Navigate, useLocation } from "react-router-dom"

import { useAuth } from "@/lib/auth/AuthProvider"

interface ProtectedRouteProps {
  children: ReactNode
  requiredUserType: "teacher" | "student"
}

export function ProtectedRoute({ children, requiredUserType }: ProtectedRouteProps) {
  const { user, token } = useAuth()
  const location = useLocation()

  if (!token || !user) {
    const redirect = encodeURIComponent(location.pathname + location.search)
    return <Navigate to={`/login?redirect=${redirect}`} replace />
  }

  if (user.userType === requiredUserType) {
    return children
  }

  if (user.userType === "teacher" || user.userType === "student") {
    const portalRoot = user.userType === "teacher" ? "/teacher" : "/student"
    return <Navigate to={portalRoot} replace />
  }

  return <Navigate to="/login" replace />
}
