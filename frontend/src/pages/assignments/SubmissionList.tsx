import { useTranslation } from "react-i18next"

export function SubmissionList() {
  const { t } = useTranslation()
  return (
    <p className="text-muted-foreground">
      {t("assignments:submissionsPlaceholder")}
    </p>
  )
}
