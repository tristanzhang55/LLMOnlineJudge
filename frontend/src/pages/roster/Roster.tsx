import { Link, useSearchParams } from "react-router-dom"
import { useState } from "react"
import { useTranslation } from "react-i18next"

import { Button, buttonVariants } from "@/components/ui/button"
import { cn } from "@/lib/utils"
import { RosterEnrollmentList } from "@/pages/roster/RosterEnrollmentList"
import { RosterImportPanel } from "@/pages/roster/RosterImportPanel"
import { RosterSingleForm } from "@/pages/roster/RosterSingleForm"

export function Roster() {
  const { t } = useTranslation()
  const [searchParams, setSearchParams] = useSearchParams()
  const tab = searchParams.get("tab") === "single" ? "single" : "import"
  const [forbidden, setForbidden] = useState(false)
  const [bulkPanelOpen, setBulkPanelOpen] = useState(false)
  const [listReloadKey, setListReloadKey] = useState(0)

  function setTab(next: "import" | "single") {
    const params = new URLSearchParams(searchParams)
    if (next === "single") {
      params.set("tab", "single")
    } else {
      params.delete("tab")
    }
    setSearchParams(params, { replace: true })
  }

  function onClickBulk() {
    if (tab === "single") {
      setTab("import")
      setBulkPanelOpen(true)
      return
    }
    setBulkPanelOpen((prev) => !prev)
  }

  function onClickSingle() {
    if (tab === "single") {
      setTab("import")
      return
    }
    setBulkPanelOpen(false)
    setTab("single")
  }

  if (forbidden) {
    return (
      <div className="flex flex-col items-center justify-center gap-4 py-16 text-center">
        <p className="text-muted-foreground">{t("forbidden")}</p>
        <Link to="/teacher" className={cn(buttonVariants({ variant: "outline" }))}>
          {t("backHome")}
        </Link>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center gap-2">
        <Button
          type="button"
          variant={bulkPanelOpen && tab === "import" ? "secondary" : "outline"}
          onClick={onClickBulk}
        >
          {t("roster:tabs.import")}
        </Button>
        <Button
          type="button"
          variant={tab === "single" ? "secondary" : "outline"}
          onClick={onClickSingle}
        >
          {t("roster:tabs.single")}
        </Button>
      </div>

      {bulkPanelOpen && tab === "import" ? (
        <RosterImportPanel
          onForbidden={() => setForbidden(true)}
          onRosterChanged={() => setListReloadKey((key) => key + 1)}
        />
      ) : null}
      {tab === "single" ? (
        <RosterSingleForm
          onForbidden={() => setForbidden(true)}
          onCancel={() => setTab("import")}
          onRosterChanged={() => setListReloadKey((key) => key + 1)}
        />
      ) : null}
      <RosterEnrollmentList
        reloadKey={listReloadKey}
        onForbidden={() => setForbidden(true)}
      />
    </div>
  )
}
