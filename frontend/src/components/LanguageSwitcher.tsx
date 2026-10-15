import { useTranslation } from "react-i18next"

import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
} from "@/components/ui/select"
import { getLocale, setLocale } from "@/i18n"

export function LanguageSwitcher() {
  const { t, i18n } = useTranslation()
  const locale = getLocale()
  const zhLabel = i18n.getFixedT("zh-CN")("simplifiedChinese")
  const enLabel = i18n.getFixedT("en-US")("english")

  return (
    <Select
      value={locale}
      onValueChange={(value) => {
        if (value === "zh-CN" || value === "en-US") {
          setLocale(value)
        }
      }}
    >
      <SelectTrigger size="sm" aria-label={t("languageSwitcher")}>
        {locale === "en-US" ? enLabel : zhLabel}
      </SelectTrigger>
      <SelectContent>
        <SelectItem value="zh-CN">{zhLabel}</SelectItem>
        <SelectItem value="en-US">{enLabel}</SelectItem>
      </SelectContent>
    </Select>
  )
}
