import { cleanup } from "@testing-library/react"
import { afterEach } from "vitest"

import { i18n, setLocale } from "@/i18n"

import "@testing-library/jest-dom/vitest"

await i18n.changeLanguage("zh-CN")

afterEach(() => {
  cleanup()
  setLocale("zh-CN")
})
