import { useCallback, useState } from "react"
import { useTranslation } from "react-i18next"
import { Link } from "react-router-dom"

import { buttonVariants } from "@/components/ui/button"
import { cn } from "@/lib/utils"
import { PasswordChangeForm } from "@/pages/student/settings/PasswordChangeForm"
import { ProfileCard } from "@/pages/student/settings/ProfileCard"

export function Settings() {
  const { t } = useTranslation()
  const [forbidden, setForbidden] = useState(false)
  const onForbidden = useCallback(() => setForbidden(true), [])

  if (forbidden) {
    return (
      <div className="flex flex-col items-center justify-center gap-4 py-16 text-center">
        <p className="text-muted-foreground">{t("forbidden")}</p>
        <Link to="/student" className={cn(buttonVariants({ variant: "outline" }))}>
          {t("backHome")}
        </Link>
      </div>
    )
  }

  return (
    <div className="max-w-xl space-y-6">
      <PasswordChangeForm onForbidden={onForbidden} />
      <ProfileCard onForbidden={onForbidden} />
    </div>
  )
}
