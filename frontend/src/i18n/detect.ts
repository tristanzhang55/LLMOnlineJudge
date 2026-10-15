export type AppLocale = "zh-CN" | "en-US"

export const LOCALE_STORAGE_KEY = "locale"
export const APP_LOCALES: readonly AppLocale[] = ["zh-CN", "en-US"]

export function isAppLocale(value: string | null | undefined): value is AppLocale {
  return value === "zh-CN" || value === "en-US"
}

export function readSavedLocale(): AppLocale | null {
  try {
    const saved = localStorage.getItem(LOCALE_STORAGE_KEY)
    return isAppLocale(saved) ? saved : null
  } catch {
    return null
  }
}

export function saveLocale(locale: AppLocale): void {
  try {
    localStorage.setItem(LOCALE_STORAGE_KEY, locale)
  } catch {
    // private mode / disabled storage
  }
}

export function detectInitialLocale(): AppLocale {
  const saved = readSavedLocale()
  if (saved) {
    return saved
  }
  const language = typeof navigator !== "undefined" ? navigator.language : ""
  if (language.toLowerCase().startsWith("zh")) {
    return "zh-CN"
  }
  return "en-US"
}
