import { useState } from "react"
import { useTranslation } from "react-i18next"
import { useNavigate } from "react-router-dom"

import { LanguageSwitcher } from "@/components/LanguageSwitcher"
import { Button } from "@/components/ui/button"
import { Separator } from "@/components/ui/separator"
import { useAuth } from "@/lib/auth/AuthProvider"

export function SidebarUserFooter() {
  const { t } = useTranslation("auth")
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const [loggingOut, setLoggingOut] = useState(false)

  if (!user) {
    return null
  }

  const handleLogout = () => {
    if (loggingOut) {
      return
    }
    setLoggingOut(true)
    void logout().finally(() => {
      navigate("/login", { replace: true })
    })
  }

  return (
    <>
      <Separator />
      <div className="flex flex-col gap-2 p-3">
        <span className="truncate px-3 text-sm text-muted-foreground">
          {user.userName ?? user.userAccount}
        </span>
        <LanguageSwitcher />
        <Button type="button" variant="outline" size="sm" disabled={loggingOut} onClick={handleLogout}>
          {loggingOut ? t("signingOut") : t("signOut")}
        </Button>
      </div>
    </>
  )
}
