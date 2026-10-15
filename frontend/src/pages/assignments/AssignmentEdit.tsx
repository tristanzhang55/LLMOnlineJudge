import { useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import { Link, useNavigate, useParams } from "react-router-dom"
import { X } from "lucide-react"

import { AssignmentForm } from "@/components/assignments/AssignmentForm"
import { Button, buttonVariants } from "@/components/ui/button"
import { ApiError } from "@/lib/api/client"
import {
  experimentToFormPayload,
  getExperimentById,
  updateExperiment,
} from "@/lib/api/experiments"
import { type AssignmentFormPayload } from "@/lib/mock-assignments"
import { cn } from "@/lib/utils"

function AssignmentNotFound() {
  const { t } = useTranslation()
  return (
    <div className="flex flex-col items-center justify-center gap-4 py-16 text-center">
      <p className="text-muted-foreground">{t("assignments:edit.notFound")}</p>
      <Link to="/teacher/assignments" className={cn(buttonVariants({ variant: "outline" }))}>
        {t("assignments:edit.back")}
      </Link>
    </div>
  )
}

function AssignmentLoadError({ onRetry }: { onRetry: () => void }) {
  const { t } = useTranslation()
  return (
    <div className="flex flex-col items-center justify-center gap-4 py-16 text-center">
      <p className="text-muted-foreground">{t("assignments:edit.loadFailed")}</p>
      <div className="flex gap-2">
        <Button type="button" onClick={onRetry}>
          {t("retry")}
        </Button>
        <Link to="/teacher/assignments" className={cn(buttonVariants({ variant: "outline" }))}>
          {t("assignments:edit.back")}
        </Link>
      </div>
    </div>
  )
}

function AssignmentDeletedOnSave() {
  const { t } = useTranslation()
  return (
    <div className="flex flex-col items-center justify-center gap-4 py-16 text-center">
      <p className="text-muted-foreground">{t("assignments:edit.deleted")}</p>
      <Link to="/teacher/assignments" className={cn(buttonVariants({ variant: "outline" }))}>
        {t("assignments:edit.back")}
      </Link>
    </div>
  )
}

export function AssignmentEdit() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { id } = useParams()
  const experimentId = Number(id)
  const isValidId = Number.isSafeInteger(experimentId) && experimentId > 0

  const [initialValues, setInitialValues] = useState<AssignmentFormPayload | null>(null)
  const [loading, setLoading] = useState(isValidId)
  const [notFound, setNotFound] = useState(false)
  const [loadError, setLoadError] = useState(false)
  const [deletedOnSave, setDeletedOnSave] = useState(false)
  const [saveConflictError, setSaveConflictError] = useState<string | null>(null)
  const [retryKey, setRetryKey] = useState(0)
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    if (!isValidId) {
      return
    }

    const controller = new AbortController()

    void (async () => {
      await Promise.resolve()
      if (controller.signal.aborted) {
        return
      }

      setLoading(true)
      setLoadError(false)
      setNotFound(false)

      try {
        const experiment = await getExperimentById(experimentId, controller.signal)
        if (controller.signal.aborted) {
          return
        }

        if (!experiment) {
          setNotFound(true)
          setInitialValues(null)
        } else {
          setInitialValues(experimentToFormPayload(experiment))
          setNotFound(false)
        }
      } catch {
        if (controller.signal.aborted) {
          return
        }
        setLoadError(true)
        setInitialValues(null)
      } finally {
        if (!controller.signal.aborted) {
          setLoading(false)
        }
      }
    })()

    return () => controller.abort()
  }, [experimentId, retryKey, isValidId])

  async function handleSubmit(values: AssignmentFormPayload) {
    if (!isValidId) {
      return
    }

    setSubmitting(true)
    setSaveConflictError(null)
    try {
      await updateExperiment(experimentId, { ...values, classId: values.classId! })
      navigate("/teacher/assignments")
    } catch (error) {
      if (error instanceof ApiError && error.status === 404) {
        setDeletedOnSave(true)
        setInitialValues(null)
        return
      }
      if (error instanceof ApiError && error.status === 409) {
        setSaveConflictError(error.message)
        return
      }
      throw error
    } finally {
      setSubmitting(false)
    }
  }

  if (!isValidId) {
    return <AssignmentNotFound />
  }

  if (deletedOnSave) {
    return <AssignmentDeletedOnSave />
  }

  if (loading) {
    return <div className="py-16 text-center text-muted-foreground">{t("loading")}</div>
  }

  if (loadError) {
    return <AssignmentLoadError onRetry={() => setRetryKey((current) => current + 1)} />
  }

  if (notFound || !initialValues) {
    return <AssignmentNotFound />
  }

  return (
    <div className="space-y-4">
      {saveConflictError && (
        <div
          role="alert"
          className="mx-auto flex max-w-4xl items-center justify-between gap-4 rounded-md border border-destructive/50 bg-destructive/10 px-4 py-3 text-sm text-destructive"
        >
          <p>{saveConflictError}</p>
          <Button
            type="button"
            variant="ghost"
            size="icon-sm"
            aria-label={t("close")}
            className="shrink-0 text-destructive hover:text-destructive"
            onClick={() => setSaveConflictError(null)}
          >
            <X className="size-4" />
          </Button>
        </div>
      )}

      <AssignmentForm
        key={experimentId}
        initialValues={initialValues}
        onSubmit={handleSubmit}
        submitting={submitting}
      />
    </div>
  )
}
