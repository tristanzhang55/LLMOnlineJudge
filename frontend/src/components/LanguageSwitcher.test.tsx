import { render, screen } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { useTranslation } from "react-i18next"
import { afterEach, describe, expect, it } from "vitest"

import { LanguageSwitcher } from "@/components/LanguageSwitcher"
import { LOCALE_STORAGE_KEY } from "@/i18n/detect"
import { setLocale } from "@/i18n"

function Probe() {
  const { t } = useTranslation()
  return (
    <div>
      <span>{t("nav:home")}</span>
      <LanguageSwitcher />
    </div>
  )
}

describe("LanguageSwitcher", () => {
  afterEach(() => {
    setLocale("zh-CN")
    localStorage.removeItem(LOCALE_STORAGE_KEY)
  })

  it("switches to English, re-renders a nav label, and writes localStorage.locale", async () => {
    const user = userEvent.setup()
    render(<Probe />)
    expect(screen.getByText("首页")).toBeInTheDocument()
    await user.click(screen.getByRole("combobox", { name: "语言" }))
    await user.click(screen.getByRole("option", { name: "English", hidden: true }))
    expect(await screen.findByText("Home")).toBeInTheDocument()
    expect(localStorage.getItem(LOCALE_STORAGE_KEY)).toBe("en-US")
  })
})
