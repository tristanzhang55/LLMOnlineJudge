import { useEffect } from "react"
import { useTranslation } from "react-i18next"

export function useDocumentTitle(pageTitle: string | undefined) {
  const { i18n } = useTranslation()

  useEffect(() => {
    document.title = pageTitle ? `${pageTitle} · Linux Judge` : "Linux Judge"
  }, [pageTitle, i18n.language])
}
