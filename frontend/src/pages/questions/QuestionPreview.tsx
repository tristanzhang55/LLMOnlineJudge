import { useTranslation } from "react-i18next"
import { Link, useLocation, useNavigate } from "react-router-dom"

import { QuestionStudentView } from "@/components/questions/QuestionStudentView"
import { Button, buttonVariants } from "@/components/ui/button"
import type { QuestionFormPayload } from "@/lib/mock-questions"
import { cn } from "@/lib/utils"

interface PreviewLocationState {
  draft?: QuestionFormPayload
  returnTo?: string
}

export function QuestionPreview() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const location = useLocation()
  const previewState = location.state as PreviewLocationState | null
  const draft = previewState?.draft
  const returnTo = previewState?.returnTo ?? "/teacher/questions"

  if (!draft) {
    return (
      <div className="flex flex-col items-center justify-center gap-4 py-16 text-center">
        <p className="text-muted-foreground">
          {t("questions:preview.stale")}
        </p>
        <Link to="/teacher/questions" className={cn(buttonVariants({ variant: "outline" }))}>
          {t("questions:edit.backBank")}
        </Link>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between gap-4">
        <p className="text-sm text-muted-foreground">{t("questions:preview.studentDraft")}</p>
        <Button
          type="button"
          variant="outline"
          onClick={() => navigate(returnTo, { state: { draft } })}
        >
          {t("back")}
        </Button>
      </div>
      <QuestionStudentView
        title={draft.title}
        content={draft.content}
        subQuestions={draft.subQuestions}
      />
    </div>
  )
}
