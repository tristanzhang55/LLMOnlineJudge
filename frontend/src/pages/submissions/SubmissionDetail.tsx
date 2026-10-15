import { useCallback, useEffect, useMemo, useState } from "react"
import { useTranslation } from "react-i18next"
import { Link, useLocation, useParams, useSearchParams } from "react-router-dom"

import { Badge } from "@/components/ui/badge"
import { Button, buttonVariants } from "@/components/ui/button"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { ApiError } from "@/lib/api/client"
import {
  answerBlocksToFormValues,
  answerBlocksToImageValues,
  formatAwardedScore,
  type StudentImageAnswer,
} from "@/lib/api/student-experiments"
import {
  fetchTeacherStudentSubmissions,
  ROUND_STATUS_BADGE_CLASS,
  type TeacherStudentSubmissionDetail,
} from "@/lib/api/teacher-submissions"
import { formatDeadlineDisplay } from "@/lib/mock-assignments"
import { cn } from "@/lib/utils"

import { TeacherQuestionCard } from "@/pages/submissions/TeacherQuestionCard"

function isValidId(value: string | undefined): number | null {
  const numeric = Number(value)
  if (!Number.isSafeInteger(numeric) || numeric <= 0) {
    return null
  }
  return numeric
}

export function SubmissionDetail() {
  const { t } = useTranslation()
  const { experimentId: experimentIdParam, studentId: studentIdParam } = useParams()
  const [searchParams] = useSearchParams()
  const location = useLocation()
  const clickIn = location.state as {
    experimentTitle?: string
    questionTitle?: string
    studentName?: string
  } | null
  const experimentId = isValidId(experimentIdParam)
  const studentId = isValidId(studentIdParam)
  const highlightSubmissionId = isValidId(searchParams.get("submissionId") ?? undefined)
  const highlightQuestionId = isValidId(searchParams.get("questionId") ?? undefined)
  const fromDisputes = highlightSubmissionId != null || highlightQuestionId != null
  const isValid = experimentId != null && studentId != null

  const [detail, setDetail] = useState<TeacherStudentSubmissionDetail | null>(null)
  const [loading, setLoading] = useState(isValid)
  const [notFound, setNotFound] = useState(!isValid)
  const [forbidden, setForbidden] = useState(false)
  const [loadError, setLoadError] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [reloadKey, setReloadKey] = useState(0)
  const [roundIndex, setRoundIndex] = useState(0)

  const loadDetail = useCallback(
    async (signal?: AbortSignal, quiet = false) => {
      if (experimentId == null || studentId == null) {
        setNotFound(true)
        setLoading(false)
        return
      }

      if (!quiet) {
        setLoading(true)
      }
      setNotFound(false)
      setForbidden(false)
      setLoadError(false)
      setErrorMessage(null)

      try {
        const result = await fetchTeacherStudentSubmissions(experimentId, studentId, signal)
        if (signal?.aborted) {
          return
        }
        setDetail(result)
        const matchedRound = highlightSubmissionId == null
          ? 0
          : Math.max(
              0,
              result.rounds.findIndex((round) =>
                round.questions.some((question) => question.submissionId === highlightSubmissionId),
              ),
            )
        setRoundIndex(matchedRound)
      } catch (err) {
        if (signal?.aborted || (err instanceof DOMException && err.name === "AbortError")) {
          return
        }
        setDetail(null)
        if (err instanceof ApiError && err.status === 404) {
          setNotFound(true)
          return
        }
        if (err instanceof ApiError && err.status === 403) {
          setForbidden(true)
          return
        }
        setLoadError(true)
        setErrorMessage(err instanceof ApiError ? err.message : null)
      } finally {
        if (!signal?.aborted) {
          setLoading(false)
        }
      }
    },
    [experimentId, studentId, highlightSubmissionId],
  )

  useEffect(() => {
    const controller = new AbortController()
    void loadDetail(controller.signal)
    return () => controller.abort()
  }, [loadDetail, reloadKey])

  const selectedRound = detail?.rounds[roundIndex] ?? detail?.rounds[0] ?? null

  const visibleQuestions = useMemo(() => {
    if (!selectedRound) {
      return []
    }
    const withClickInTitle = selectedRound.questions.map((question, index) => ({
      question:
        !question.questionTitle.trim() &&
        clickIn?.questionTitle?.trim() &&
        question.experimentQuestionId === highlightQuestionId
          ? { ...question, questionTitle: clickIn.questionTitle }
          : question,
      index,
    }))
    if (highlightQuestionId == null) {
      return withClickInTitle
    }
    const matched = withClickInTitle.find(
      (item) => item.question.experimentQuestionId === highlightQuestionId,
    )
    if (!matched) {
      return withClickInTitle
    }
    return [matched]
  }, [clickIn?.questionTitle, highlightQuestionId, selectedRound])

  const answerMaps = useMemo(() => {
    const textValues: Record<number, string> = {}
    const imageValues: Record<number, StudentImageAnswer | null> = {}
    if (!selectedRound) {
      return { textValues, imageValues }
    }
    for (const question of selectedRound.questions) {
      for (const sub of question.subQuestions) {
        Object.assign(textValues, answerBlocksToFormValues([sub.studentAnswerBlocks]))
        Object.assign(imageValues, answerBlocksToImageValues([sub.studentAnswerBlocks]))
      }
    }
    return { textValues, imageValues }
  }, [selectedRound])

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

  if (loading) {
    return <div className="py-16 text-center text-muted-foreground">{t("loading")}</div>
  }

  if (notFound) {
    return (
      <div className="flex flex-col items-center justify-center gap-4 py-16 text-center">
        <p className="text-muted-foreground">{t("submissions:detail.notFound")}</p>
        <Link to="/teacher/submissions" className={cn(buttonVariants({ variant: "outline" }))}>
          {t("submissions:detail.backList")}
        </Link>
      </div>
    )
  }

  if (loadError) {
    return (
      <div className="flex flex-col items-center justify-center gap-4 py-16 text-center">
        <p className="text-muted-foreground">{errorMessage || t("submissions:detail.loadFailed")}</p>
        <div className="flex gap-2">
          <Button type="button" onClick={() => setReloadKey((key) => key + 1)}>
            {t("retry")}
          </Button>
          <Link to="/teacher/submissions" className={cn(buttonVariants({ variant: "outline" }))}>
            {t("submissions:detail.backList")}
          </Link>
        </div>
      </div>
    )
  }

  if (!detail || detail.rounds.length === 0 || !selectedRound) {
    return (
      <div className="flex flex-col items-center justify-center gap-4 py-16 text-center">
        <p className="text-muted-foreground">{t("submissions:detail.noRounds")}</p>
        <Link to="/teacher/submissions" className={cn(buttonVariants({ variant: "outline" }))}>
          {t("submissions:detail.backList")}
        </Link>
      </div>
    )
  }

  const roundScore =
    selectedRound.status === "completed" && selectedRound.score != null
      ? formatAwardedScore(selectedRound.score)
      : "—"

  return (
    <div className="mx-auto max-w-4xl space-y-6">
      <div className="space-y-3">
        <h1 className="text-2xl font-semibold">
          {detail.experimentTitle || clickIn?.experimentTitle || t("unnamedAssignment")}
        </h1>
        <div className="flex flex-wrap items-center gap-3 text-sm text-muted-foreground">
          <span>{detail.studentName || clickIn?.studentName || t("unknownStudent")}</span>
          <span>{formatDeadlineDisplay(selectedRound.submitTime)}</span>
          <Badge className={ROUND_STATUS_BADGE_CLASS[selectedRound.status]}>
            {t(`submissions:roundStatus.${selectedRound.status}`)}
          </Badge>
          <span>{t("submissions:detail.score", { score: roundScore })}</span>
        </div>

        {detail.rounds.length > 1 ? (
          <div className="flex items-center gap-2">
            <span className="text-sm text-muted-foreground">{t("submissions:detail.round")}</span>
            <Select
              value={String(roundIndex)}
              onValueChange={(value) => setRoundIndex(Number(value ?? 0))}
            >
              <SelectTrigger className="w-[240px]">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {detail.rounds.map((round, index) => (
                  <SelectItem key={`${round.submitTime}-${index}`} value={String(index)}>
                    {formatDeadlineDisplay(round.submitTime)}
                    {index === 0 ? t("submissions:detail.latest") : ""}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        ) : null}
      </div>

      <div className="space-y-6">
        {visibleQuestions.map(({ question, index }) => (
          <TeacherQuestionCard
            key={question.experimentQuestionId}
            question={question}
            questionIndex={index}
            textValues={answerMaps.textValues}
            imageValues={answerMaps.imageValues}
            highlighted={highlightQuestionId === question.experimentQuestionId}
            onResolved={() => void loadDetail(undefined, true)}
          />
        ))}
      </div>

      <div className="flex justify-end border-t pt-6">
        <Link
          to={fromDisputes ? "/teacher/disputes" : "/teacher/submissions"}
          className={cn(buttonVariants({ variant: "outline" }))}
        >
          {fromDisputes ? t("submissions:detail.backDisputes") : t("submissions:detail.backList")}
        </Link>
      </div>
    </div>
  )
}
