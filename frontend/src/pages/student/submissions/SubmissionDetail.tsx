import { useTranslation } from "react-i18next"

export function SubmissionDetail() {
  const { t } = useTranslation()
  return (
    <p className="text-muted-foreground">
      {t("submissions:studentPlaceholder")}
    </p>
  )
}
