import { useCallback, useEffect, useMemo, useRef, useState } from "react"
import { useTranslation } from "react-i18next"
import type { TFunction } from "i18next"
import { Link, useNavigate, useParams, useSearchParams } from "react-router-dom"
import { X } from "lucide-react"

import { Badge } from "@/components/ui/badge"
import { Button, buttonVariants } from "@/components/ui/button"
import { uploadAttachment } from "@/lib/api/attachments"
import { ApiError } from "@/lib/api/client"
import {
  answerBlocksToCodeLanguages,
  answerBlocksToFormValues,
  answerBlocksToImageValues,
  buildAnswersForQuestion,
  buildSubmitAnswers,
  fetchMyExperimentScores,
  fetchMySubmissionState,
  formatHistoryAnswerText,
  getStudentExperimentById,
  saveSubmissionDraft,
  serializeAnswerBlocks,
  submitStudentExperiment,
  type MyExperimentScores,
  type MyQuestionScores,
  type MySubQuestionScore,
  type MySubmissionState,
  type StudentCodeLanguages,
  type StudentExperimentDetail,
  type StudentExperimentQuestion,
  type StudentFormValues,
  type StudentImageValues,
} from "@/lib/api/student-experiments"
import { formatDeadlineDisplay } from "@/lib/mock-assignments"
import { cn } from "@/lib/utils"

import { ConfirmSubmitDialog } from "@/pages/student/assignments/ConfirmSubmitDialog"
import { StudentQuestionAnswer } from "@/pages/student/assignments/StudentQuestionAnswer"

const STUDENT_STATUS_BADGE_CLASS = {
  1: "border-transparent bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400",
  2: "border-transparent bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-400",
} as const

const MAX_FILE_SIZE = 5 * 1024 * 1024
const ALLOWED_TYPES = ["image/png", "image/jpeg", "image/gif", "image/webp"]

function parsePositiveId(value: string | null): number | null {
  if (value == null || value === "") {
    return null
  }
  const numeric = Number(value)
  if (!Number.isSafeInteger(numeric) || numeric <= 0) {
    return null
  }
  return numeric
}

function AssignmentUnavailable() {
  const { t } = useTranslation()
  return (
    <div className="flex flex-col items-center justify-center gap-4 py-16 text-center">
      <p className="text-muted-foreground">{t("student:detail.unavailable")}</p>
      <Link to="/student/assignments" className={cn(buttonVariants({ variant: "outline" }))}>
        {t("student:detail.backAssignments")}
      </Link>
    </div>
  )
}

function AssignmentLoadError({ onRetry }: { onRetry: () => void }) {
  const { t } = useTranslation()
  return (
    <div className="flex flex-col items-center justify-center gap-4 py-16 text-center">
      <p className="text-muted-foreground">{t("student:detail.loadFailed")}</p>
      <div className="flex gap-2">
        <Button type="button" onClick={onRetry}>
          {t("retry")}
        </Button>
        <Link to="/student/assignments" className={cn(buttonVariants({ variant: "outline" }))}>
          {t("student:detail.backAssignments")}
        </Link>
      </div>
    </div>
  )
}

function formatSavedClock(date = new Date()) {
  const hours = String(date.getHours()).padStart(2, "0")
  const minutes = String(date.getMinutes()).padStart(2, "0")
  return `${hours}:${minutes}`
}

function getStatusBanner(
  detail: StudentExperimentDetail,
  state: MySubmissionState,
  t: TFunction,
): string | null {
  if (detail.status === 2) {
    return t("student:detail.closed")
  }
  if (state.locked && state.isRoundGradingComplete) {
    return t("student:detail.graded")
  }
  if (state.locked) {
    return t("student:detail.waiting")
  }
  if (state.resubmissionEligible) {
    return t("student:detail.republished")
  }
  return null
}

function applyStateToForm(
  questions: StudentExperimentQuestion[],
  state: MySubmissionState,
) {
  const formValues: StudentFormValues = {}
  const imageValues: StudentImageValues = {}
  const codeLanguages: StudentCodeLanguages = {}
  const submissionIds: Record<number, number | null> = {}
  const lastSynced: Record<number, string> = {}
  const byId = new Map(
    state.questions.map((row) => [row.experimentQuestionId, row]),
  )

  for (const question of questions) {
    const row = byId.get(question.id)
    const blocks = row?.answerBlocks ?? null
    const text = answerBlocksToFormValues(blocks)
    const images = answerBlocksToImageValues(blocks)
    const languages = answerBlocksToCodeLanguages(blocks)
    formValues[question.id] = text
    imageValues[question.id] = images
    codeLanguages[question.id] = languages
    submissionIds[question.id] = row?.submissionId ?? null
    lastSynced[question.id] = serializeAnswerBlocks(
      buildAnswersForQuestion(question, text, images, languages),
    )
  }

  return { formValues, imageValues, codeLanguages, submissionIds, lastSynced }
}

function DismissibleBanner({
  message,
  onDismiss,
  onRetry,
}: {
  message: string
  onDismiss: () => void
  onRetry?: () => void
}) {
  const { t } = useTranslation()
  return (
    <div
      role="alert"
      className="flex items-center justify-between gap-4 rounded-md border border-destructive/50 bg-destructive/10 px-4 py-3 text-sm text-destructive"
    >
      <p>{message}</p>
      <div className="flex shrink-0 items-center gap-1">
        {onRetry ? (
          <Button type="button" variant="ghost" size="sm" className="text-destructive hover:text-destructive" onClick={onRetry}>
            {t("retry")}
          </Button>
        ) : null}
        <Button
          type="button"
          variant="ghost"
          size="icon-sm"
          aria-label={t("close")}
          className="text-destructive hover:text-destructive"
          onClick={onDismiss}
        >
          <X className="size-4" />
        </Button>
      </div>
    </div>
  )
}

export function AssignmentDetail() {
  const { t } = useTranslation()
  const { id } = useParams()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const experimentId = Number(id)
  const isValidId = Number.isSafeInteger(experimentId) && experimentId > 0
  const highlightSubmissionId = parsePositiveId(searchParams.get("submissionId"))
  const highlightQuestionId = parsePositiveId(searchParams.get("questionId"))
  const fromDisputes = highlightSubmissionId != null || highlightQuestionId != null

  const [detail, setDetail] = useState<StudentExperimentDetail | null>(null)
  const [submissionState, setSubmissionState] = useState<MySubmissionState | null>(null)
  const [formValues, setFormValues] = useState<StudentFormValues>({})
  const [imageValues, setImageValues] = useState<StudentImageValues>({})
  const [codeLanguages, setCodeLanguages] = useState<StudentCodeLanguages>({})
  const [submissionIds, setSubmissionIds] = useState<Record<number, number | null>>({})
  const [loading, setLoading] = useState(isValidId)
  const [notFound, setNotFound] = useState(false)
  const [loadError, setLoadError] = useState(false)
  const [forbidden, setForbidden] = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [draftError, setDraftError] = useState<string | null>(null)
  const [confirmOpen, setConfirmOpen] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [draftSaving, setDraftSaving] = useState(false)
  const [savedLabel, setSavedLabel] = useState<string | null>(null)
  const [retryKey, setRetryKey] = useState(0)
  const [uploadingKey, setUploadingKey] = useState<string | null>(null)
  const [experimentScores, setExperimentScores] = useState<MyExperimentScores | null>(null)
  const [scoresError, setScoresError] = useState<string | null>(null)

  const formValuesRef = useRef(formValues)
  const imageValuesRef = useRef(imageValues)
  const codeLanguagesRef = useRef(codeLanguages)
  const submissionIdsRef = useRef(submissionIds)
  const lastSyncedRef = useRef<Record<number, string>>({})
  const savedLabelTimerRef = useRef<number | null>(null)

  formValuesRef.current = formValues
  imageValuesRef.current = imageValues
  codeLanguagesRef.current = codeLanguages
  submissionIdsRef.current = submissionIds

  const applyLoaded = useCallback(
    (nextDetail: StudentExperimentDetail, nextState: MySubmissionState) => {
      const applied = applyStateToForm(nextDetail.questions, nextState)
      setDetail(nextDetail)
      setSubmissionState(nextState)
      setFormValues(applied.formValues)
      setImageValues(applied.imageValues)
      setCodeLanguages(applied.codeLanguages)
      setSubmissionIds(applied.submissionIds)
      lastSyncedRef.current = applied.lastSynced
    },
    [],
  )

  const loadDetail = useCallback(
    async (signal?: AbortSignal, options?: { silent?: boolean }) => {
      if (!isValidId) {
        return
      }

      const silent = options?.silent === true
      if (!silent) {
        setLoading(true)
      }
      setLoadError(false)
      setNotFound(false)
      setForbidden(false)

      try {
        const [result, state] = await Promise.all([
          getStudentExperimentById(experimentId, signal),
          fetchMySubmissionState(experimentId, signal),
        ])
        if (signal?.aborted) {
          return
        }

        if (!result) {
          setNotFound(true)
          setDetail(null)
          setSubmissionState(null)
          setExperimentScores(null)
          setScoresError(null)
          return
        }

        applyLoaded(result, state)
        setNotFound(false)

        if (!silent) {
          setLoading(false)
        }

        if (state.isRoundGradingComplete) {
          try {
            const scores = await fetchMyExperimentScores(experimentId, signal)
            if (signal?.aborted) {
              return
            }
            setExperimentScores(scores)
            setScoresError(null)
          } catch (scoresErr) {
            if (signal?.aborted || (scoresErr instanceof DOMException && scoresErr.name === "AbortError")) {
              return
            }
            setExperimentScores(null)
            if (scoresErr instanceof ApiError) {
              setScoresError(scoresErr.message || t("student:detail.scoresFailed"))
            } else {
              setScoresError(t("student:detail.scoresFailed"))
            }
          }
        } else {
          setExperimentScores(null)
          setScoresError(null)
        }
      } catch (err) {
        if (signal?.aborted || (err instanceof DOMException && err.name === "AbortError")) {
          return
        }
        if (err instanceof ApiError && err.status === 403) {
          setForbidden(true)
          setDetail(null)
          setSubmissionState(null)
          setExperimentScores(null)
          return
        }
        if (err instanceof ApiError && err.status === 404) {
          setNotFound(true)
          setDetail(null)
          setSubmissionState(null)
          setExperimentScores(null)
          return
        }
        setLoadError(true)
        setDetail(null)
        setSubmissionState(null)
        setExperimentScores(null)
      } finally {
        if (!signal?.aborted && !silent) {
          setLoading(false)
        }
      }
    },
    [applyLoaded, experimentId, isValidId, t],
  )

  useEffect(() => {
    if (!isValidId) {
      return
    }

    const controller = new AbortController()
    void loadDetail(controller.signal)
    return () => controller.abort()
  }, [loadDetail, retryKey, isValidId])

  useEffect(() => {
    return () => {
      if (savedLabelTimerRef.current != null) {
        window.clearTimeout(savedLabelTimerRef.current)
      }
    }
  }, [])

  const disputeQuestionIndex =
    detail != null && highlightQuestionId != null
      ? detail.questions.findIndex((question) => question.id === highlightQuestionId)
      : -1
  const showDisputeQuestionOnly = disputeQuestionIndex >= 0

  const editable = submissionState?.editable === true
  const statusBanner =
    detail && submissionState ? getStatusBanner(detail, submissionState, t) : null

  const questionTitleMap = useMemo(() => {
    const map = new Map<number, string>()
    for (const question of detail?.questions ?? []) {
      map.set(question.id, question.questionTitle || t("emptyTitle"))
    }
    return map
  }, [detail, t])

  const questionScoresById = useMemo(() => {
    const map = new Map<number, MyQuestionScores>()
    for (const row of experimentScores?.questions ?? []) {
      map.set(row.experimentQuestionId, row)
    }
    return map
  }, [experimentScores])

  const loadScores = useCallback(
    async (signal?: AbortSignal) => {
      try {
        const scores = await fetchMyExperimentScores(experimentId, signal)
        if (signal?.aborted) {
          return
        }
        setExperimentScores(scores)
        setScoresError(null)
      } catch (err) {
        if (signal?.aborted || (err instanceof DOMException && err.name === "AbortError")) {
          return
        }
        setExperimentScores(null)
        if (err instanceof ApiError) {
          setScoresError(err.message || t("student:detail.scoresFailed"))
        } else {
          setScoresError(t("student:detail.scoresFailed"))
        }
      }
    },
    [experimentId, t],
  )

  function updateAnswer(questionId: number, subQuestionId: number, content: string) {
    setFormValues((current) => ({
      ...current,
      [questionId]: {
        ...(current[questionId] ?? {}),
        [subQuestionId]: content,
      },
    }))
  }

  async function handleDraftFailure(err: unknown) {
    if (err instanceof ApiError && err.status === 403) {
      setDraftError(t("student:detail.alreadySubmitted"))
      await loadDetail(undefined, { silent: true })
      return
    }
    if (err instanceof ApiError && err.status === 409) {
      setDraftError(err.message || t("student:detail.unpublished"))
      return
    }
    if (err instanceof ApiError && err.status === 404) {
      setDraftError(t("student:detail.unavailable"))
      return
    }
    if (err instanceof ApiError) {
      setDraftError(err.message || t("student:detail.draftFailed"))
      return
    }
    setDraftError(t("student:detail.draftFailed"))
  }

  async function persistQuestionDraft(
    question: StudentExperimentQuestion,
    options?: { force?: boolean },
  ): Promise<number | null> {
    const text = formValuesRef.current[question.id] ?? {}
    const images = imageValuesRef.current[question.id] ?? {}
    const languages = codeLanguagesRef.current[question.id] ?? {}
    const answerBlocks = buildAnswersForQuestion(question, text, images, languages)
    const serialized = serializeAnswerBlocks(answerBlocks)
    const existingId = submissionIdsRef.current[question.id] ?? null

    if (!options?.force && serialized === lastSyncedRef.current[question.id]) {
      return existingId
    }

    const { id: savedId } = await saveSubmissionDraft({
      experimentQuestionId: question.id,
      answerBlocks,
    })
    submissionIdsRef.current = {
      ...submissionIdsRef.current,
      [question.id]: savedId,
    }
    lastSyncedRef.current = {
      ...lastSyncedRef.current,
      [question.id]: serialized,
    }
    setSubmissionIds(submissionIdsRef.current)
    setDraftError(null)
    return savedId
  }

  async function handleQuestionBlur(question: StudentExperimentQuestion) {
    if (!editable) {
      return
    }
    try {
      await persistQuestionDraft(question)
    } catch (err) {
      await handleDraftFailure(err)
    }
  }

  async function handleImageUpload(question: StudentExperimentQuestion, subQuestionId: number, file: File) {
    if (!editable) {
      return
    }

    if (file.size > MAX_FILE_SIZE) {
      setDraftError(t("student:detail.imageTooLarge"))
      return
    }
    if (!ALLOWED_TYPES.includes(file.type)) {
      setDraftError(t("student:detail.imageType"))
      return
    }

    setUploadingKey(`${question.id}-${subQuestionId}`)
    setDraftError(null)

    try {
      let ownerId = submissionIdsRef.current[question.id]
      if (ownerId == null) {
        ownerId = await persistQuestionDraft(question, { force: true })
      }
      if (ownerId == null) {
        setDraftError(t("student:detail.draftFailed"))
        return
      }

      const uploaded = await uploadAttachment(file, "submission", ownerId)
      const nextImage = { attachmentId: uploaded.id, url: uploaded.url }
      const nextImages: StudentImageValues = {
        ...imageValuesRef.current,
        [question.id]: {
          ...(imageValuesRef.current[question.id] ?? {}),
          [subQuestionId]: nextImage,
        },
      }
      imageValuesRef.current = nextImages
      setImageValues(nextImages)
      await persistQuestionDraft(question, { force: true })
    } catch (err) {
      await handleDraftFailure(err)
    } finally {
      setUploadingKey(null)
    }
  }

  function handleImageRemove(question: StudentExperimentQuestion, subQuestionId: number) {
    const nextImages: StudentImageValues = {
      ...imageValuesRef.current,
      [question.id]: {
        ...(imageValuesRef.current[question.id] ?? {}),
        [subQuestionId]: null,
      },
    }
    imageValuesRef.current = nextImages
    setImageValues(nextImages)
    void persistQuestionDraft(question, { force: true }).catch((err) => {
      void handleDraftFailure(err)
    })
  }

  function showSavedConfirmation() {
    setSavedLabel(t("student:detail.savedAt", { time: formatSavedClock() }))
    if (savedLabelTimerRef.current != null) {
      window.clearTimeout(savedLabelTimerRef.current)
    }
    savedLabelTimerRef.current = window.setTimeout(() => {
      setSavedLabel(null)
      savedLabelTimerRef.current = null
    }, 2000)
  }

  async function handleSaveDrafts() {
    if (!detail || !editable) {
      return
    }

    setDraftSaving(true)
    try {
      const results = await Promise.allSettled(
        detail.questions.map((question) => persistQuestionDraft(question)),
      )
      const firstFailure = results.find((result) => result.status === "rejected")
      if (firstFailure && firstFailure.status === "rejected") {
        await handleDraftFailure(firstFailure.reason)
        return
      }
      setDraftError(null)
      showSavedConfirmation()
    } finally {
      setDraftSaving(false)
    }
  }

  async function handleConfirmSubmit() {
    if (!detail || !editable) {
      return
    }

    setSubmitting(true)
    setSubmitError(null)

    try {
      const answers = buildSubmitAnswers(
        detail.questions,
        formValuesRef.current,
        imageValuesRef.current,
        codeLanguagesRef.current,
      )
      await submitStudentExperiment(detail.id, { answers })
      setConfirmOpen(false)
      await loadDetail(undefined, { silent: true })
    } catch (err) {
      if (err instanceof ApiError && err.status === 404) {
        setNotFound(true)
        setDetail(null)
        setConfirmOpen(false)
        return
      }
      if (err instanceof ApiError && err.status === 403) {
        setForbidden(true)
        setDetail(null)
        setConfirmOpen(false)
        return
      }
      if (err instanceof ApiError && err.status === 409) {
        if (err.message === "assignment is not open for submission") {
          setSubmitError(t("student:detail.cannotSubmitClosed"))
        } else {
          setSubmitError(err.message)
        }
        setConfirmOpen(false)
        await loadDetail(undefined, { silent: true })
        return
      }
      if (err instanceof ApiError && err.status === 400) {
        if (err.message === "assignment is not open for submission") {
          setSubmitError(t("student:detail.cannotSubmitClosed"))
        } else if (err.fieldErrors?.some((item) => item.field === "answers")) {
          setSubmitError(t("student:detail.questionsChanged"))
        } else {
          setSubmitError(err.message)
        }
        setConfirmOpen(false)
        await loadDetail(undefined, { silent: true })
        return
      }
      if (err instanceof ApiError) {
        setSubmitError(err.message)
      } else {
        setSubmitError(t("student:detail.submitFailed"))
      }
    } finally {
      setSubmitting(false)
    }
  }

  if (!isValidId) {
    return <AssignmentUnavailable />
  }

  if (forbidden) {
    return (
      <div className="flex flex-col items-center justify-center gap-4 py-16 text-center">
        <p className="text-muted-foreground">{t("forbidden")}</p>
        <Link to="/student" className={cn(buttonVariants({ variant: "outline" }))}>
          {t("backHome")}
        </Link>
      </div>
    )
  }

  if (loading) {
    return <div className="py-16 text-center text-muted-foreground">{t("loading")}</div>
  }

  if (loadError) {
    return <AssignmentLoadError onRetry={() => setRetryKey((current) => current + 1)} />
  }

  if (notFound || !detail || !submissionState) {
    return <AssignmentUnavailable />
  }

  return (
    <div className="mx-auto max-w-4xl space-y-6">
      <div className="space-y-3">
        <div className="flex flex-wrap items-center gap-3">
          <h1 className="text-2xl font-semibold">{detail.title || t("unnamedAssignment")}</h1>
          <Badge className={STUDENT_STATUS_BADGE_CLASS[detail.status]}>
            {t(`assignments:status.${detail.status}`)}
          </Badge>
        </div>

        {detail.description?.trim() ? (
          <p className="whitespace-pre-wrap text-muted-foreground">{detail.description}</p>
        ) : null}

        <p className="text-sm text-muted-foreground">
          {t("student:detail.deadline")}
          {detail.deadlineTime == null ? "—" : formatDeadlineDisplay(detail.deadlineTime)}
        </p>
      </div>

      {statusBanner && (
        <div className="rounded-md border bg-muted/40 px-4 py-3 text-sm text-muted-foreground">
          {statusBanner}
        </div>
      )}

      {scoresError && (
        <DismissibleBanner
          message={scoresError}
          onDismiss={() => setScoresError(null)}
          onRetry={() => void loadScores()}
        />
      )}

      {draftError && (
        <DismissibleBanner message={draftError} onDismiss={() => setDraftError(null)} />
      )}

      {submitError && (
        <DismissibleBanner message={submitError} onDismiss={() => setSubmitError(null)} />
      )}

      <div className="space-y-6">
        {(showDisputeQuestionOnly
          ? [{ question: detail.questions[disputeQuestionIndex], index: disputeQuestionIndex }]
          : detail.questions.map((question, index) => ({ question, index }))
        ).map(({ question, index }) => {
          const scoreRow = questionScoresById.get(question.id)
          const subQuestionScores: Record<number, MySubQuestionScore> = {}
          for (const sub of scoreRow?.subQuestions ?? []) {
            subQuestionScores[sub.subQuestionId] = sub
          }
          return (
          <StudentQuestionAnswer
            key={question.id}
            question={question}
            questionIndex={index}
            editable={editable}
            values={formValues[question.id] ?? {}}
            imageValues={imageValues[question.id] ?? {}}
            onChange={(subQuestionId, content) => updateAnswer(question.id, subQuestionId, content)}
            onBlurQuestion={() => void handleQuestionBlur(question)}
            onImageUpload={(subQuestionId, file) =>
              void handleImageUpload(question, subQuestionId, file)
            }
            onImageRemove={(subQuestionId) => handleImageRemove(question, subQuestionId)}
            uploadingSubQuestionId={
              uploadingKey?.startsWith(`${question.id}-`)
                ? Number(uploadingKey.slice(`${question.id}-`.length))
                : null
            }
            questionScore={scoreRow?.status === 2 ? scoreRow.score : null}
            questionStatus={scoreRow?.status ?? null}
            subQuestionScores={subQuestionScores}
            experimentId={experimentId}
            submissionId={scoreRow?.submissionId ?? null}
            disputeId={scoreRow?.disputeId ?? null}
            disputeStatus={scoreRow?.disputeStatus ?? 0}
            disputeReason={scoreRow?.disputeReason ?? null}
            manualComment={scoreRow?.manualComment ?? null}
            highlighted={highlightQuestionId === question.id}
            onDisputeFiled={() => void loadScores()}
            onWithdrawn={() => void loadScores()}
          />
          )
        })}
      </div>

      {!showDisputeQuestionOnly && detail.history.length > 0 && (
        <section className="space-y-4 rounded-xl border border-dashed bg-muted/20 px-4 py-5">
          <h2 className="text-lg font-semibold text-muted-foreground">{t("student:detail.history")}</h2>
          {detail.history.map((round) => (
            <div key={round.submitTime} className="space-y-3 rounded-lg border bg-background px-4 py-4">
              <div className="flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
                <span>{round.roundLabel}</span>
                <span>{formatDeadlineDisplay(round.submitTime)}</span>
              </div>
              {round.answers.map((answer) => (
                <div key={`${answer.experimentQuestionId}-${answer.submitTime}`} className="space-y-1">
                  <p className="text-sm font-medium">
                    {questionTitleMap.get(answer.experimentQuestionId) ?? t("student:detail.question")}
                  </p>
                  <pre className="whitespace-pre-wrap rounded-md bg-muted/40 px-3 py-2 text-sm">
                    {formatHistoryAnswerText(answer.answerBlocks, { image: t("student:detail.imagePlaceholder"), empty: t("student:detail.noAnswer") })}
                  </pre>
                </div>
              ))}
            </div>
          ))}
        </section>
      )}

      <div className="flex justify-end gap-2 border-t pt-6">
        {editable && (
          <Button
            type="button"
            variant="outline"
            disabled={draftSaving}
            onClick={() => void handleSaveDrafts()}
          >
            {draftSaving ? t("student:detail.saving") : (savedLabel ?? t("student:detail.saveDraft"))}
          </Button>
        )}
        <Button
          type="button"
          variant="outline"
          onClick={() => navigate(fromDisputes ? "/student/disputes" : "/student/assignments")}
        >
          {fromDisputes ? t("student:detail.backMyDisputes") : t("back")}
        </Button>
        {editable && (
          <Button type="button" disabled={submitting} onClick={() => setConfirmOpen(true)}>
            {t("student:detail.confirmSubmit")}
          </Button>
        )}
      </div>

      <ConfirmSubmitDialog
        open={confirmOpen}
        onOpenChange={(open) => {
          if (!submitting) {
            setConfirmOpen(open)
          }
        }}
        onConfirm={handleConfirmSubmit}
        loading={submitting}
      />
    </div>
  )
}
