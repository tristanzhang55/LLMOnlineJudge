import type { ParseKeys } from "i18next"
import { useTranslation } from "react-i18next"
import { NavLink, Outlet, useLocation, useMatches } from "react-router-dom"
import {
  AlertCircle,
  ClipboardList,
  FileText,
  LayoutDashboard,
  Settings,
} from "lucide-react"

import { Separator } from "@/components/ui/separator"
import { SidebarUserFooter } from "@/components/layout/SidebarUserFooter"
import { useDocumentTitle } from "@/hooks/useDocumentTitle"
import { cn } from "@/lib/utils"

const navItems = [
  { to: "/student", labelKey: "nav:home", icon: LayoutDashboard, end: true },
  { to: "/student/assignments", labelKey: "nav:assignments", icon: ClipboardList, end: false },
  { to: "/student/submissions", labelKey: "nav:submissions", icon: FileText, end: false },
  { to: "/student/disputes", labelKey: "nav:myDisputes", icon: AlertCircle, end: false },
  { to: "/student/settings", labelKey: "nav:settings", icon: Settings, end: false },
] as const

function readTitleKey(matches: ReturnType<typeof useMatches>): ParseKeys | undefined {
  const found = [...matches]
    .reverse()
    .find(
      (match) =>
        typeof match.handle === "object" &&
        match.handle !== null &&
        "titleKey" in match.handle &&
        typeof (match.handle as { titleKey?: unknown }).titleKey === "string",
    )
  return found ? ((found.handle as { titleKey: string }).titleKey as ParseKeys) : undefined
}

export function StudentLayout() {
  const { t } = useTranslation()
  const location = useLocation()
  const matches = useMatches()
  const titleKey = readTitleKey(matches)
  const pageTitle = titleKey ? t(titleKey) : undefined
  const isDashboard = location.pathname === "/student"

  useDocumentTitle(pageTitle)

  return (
    <div className="flex min-h-screen bg-background">
      <aside className="flex w-64 shrink-0 flex-col border-r bg-card">
        <div className="px-6 py-5">
          <span className="text-lg font-semibold tracking-tight">LLM-based Online Judge</span>
        </div>
        <Separator />
        <nav className="flex flex-1 flex-col gap-1 p-3">
          {navItems.map(({ to, labelKey, icon: Icon, end }) => (
            <NavLink
              key={to}
              to={to}
              end={end}
              className={({ isActive }) =>
                cn(
                  "flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors",
                  isActive
                    ? "bg-primary text-primary-foreground"
                    : "text-muted-foreground hover:bg-muted hover:text-foreground"
                )
              }
            >
              <Icon className="size-4 shrink-0" />
              {t(labelKey)}
            </NavLink>
          ))}
        </nav>
        <SidebarUserFooter />
      </aside>

      <main className="flex flex-1 flex-col">
        {!isDashboard && pageTitle && (
          <header className="border-b px-8 py-4">
            <h1 className="text-xl font-semibold">{pageTitle}</h1>
          </header>
        )}
        <div className="flex-1 p-8">
          <Outlet />
        </div>
      </main>
    </div>
  )
}
