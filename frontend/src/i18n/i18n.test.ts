import { afterEach, describe, expect, it } from "vitest"

import { detectInitialLocale, LOCALE_STORAGE_KEY, saveLocale } from "@/i18n/detect"
import { resources } from "@/i18n/resources"

function collectKeys(value: unknown, prefix = ""): string[] {
  if (typeof value === "string") {
    return [prefix]
  }
  if (!value || typeof value !== "object") {
    return []
  }
  return Object.entries(value as Record<string, unknown>).flatMap(([key, child]) =>
    collectKeys(child, prefix ? `${prefix}.${key}` : key),
  )
}

const CJK = /[\u4e00-\u9fff]/

describe("i18n catalogs", () => {
  it("has identical key sets per namespace", () => {
    const zh = resources["zh-CN"]
    const en = resources["en-US"]
    for (const ns of Object.keys(zh) as (keyof typeof zh)[]) {
      expect(collectKeys(en[ns]).sort()).toEqual(collectKeys(zh[ns]).sort())
    }
  })

  it("has no CJK in en-US values", () => {
    const walk = (value: unknown) => {
      if (typeof value === "string") {
        expect(value).not.toMatch(CJK)
        return
      }
      if (value && typeof value === "object") {
        for (const child of Object.values(value)) {
          walk(child)
        }
      }
    }
    walk(resources["en-US"])
  })
})

describe("detectInitialLocale", () => {
  afterEach(() => {
    localStorage.removeItem(LOCALE_STORAGE_KEY)
  })

  it("honors a saved locale", () => {
    saveLocale("en-US")
    expect(detectInitialLocale()).toBe("en-US")
  })

  it("uses navigator.language when nothing is saved", () => {
    localStorage.removeItem(LOCALE_STORAGE_KEY)
    const original = navigator.language
    Object.defineProperty(navigator, "language", { configurable: true, value: "zh-TW" })
    expect(detectInitialLocale()).toBe("zh-CN")
    Object.defineProperty(navigator, "language", { configurable: true, value: "en-GB" })
    expect(detectInitialLocale()).toBe("en-US")
    Object.defineProperty(navigator, "language", { configurable: true, value: original })
  })
})
