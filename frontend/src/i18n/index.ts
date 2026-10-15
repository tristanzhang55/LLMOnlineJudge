import i18n from "i18next"
import { initReactI18next } from "react-i18next"

import { detectInitialLocale, isAppLocale, saveLocale, type AppLocale } from "@/i18n/detect"
import { resources } from "@/i18n/resources"

export type { AppLocale }

const initialLocale = detectInitialLocale()

void i18n.use(initReactI18next).init({
  resources,
  lng: initialLocale,
  fallbackLng: "zh-CN",
  supportedLngs: ["zh-CN", "en-US"],
  defaultNS: "common",
  interpolation: { escapeValue: false },
  returnNull: false,
  initImmediate: false,
} as Parameters<typeof i18n.init>[0])

if (typeof document !== "undefined") {
  document.documentElement.lang = initialLocale
}

export function getLocale(): AppLocale {
  const resolved = i18n.resolvedLanguage ?? i18n.language
  return isAppLocale(resolved) ? resolved : "zh-CN"
}

export function setLocale(locale: AppLocale): void {
  void i18n.changeLanguage(locale)
  saveLocale(locale)
  if (typeof document !== "undefined") {
    document.documentElement.lang = locale
  }
}

export { i18n }
export default i18n
