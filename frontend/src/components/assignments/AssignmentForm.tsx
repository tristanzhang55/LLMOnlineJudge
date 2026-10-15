import { useEffect, useRef, useState } from "react"
import { enUS, zhCN } from "date-fns/locale"
import type { TFunction } from "i18next"
import { useTranslation } from "react-i18next"
import { useNavigate } from "react-router-dom"
import { CalendarIcon } from "lucide-react"

import { getLocale } from "@/i18n"

import { QuestionPickerDialog } from "@/components/assignments/QuestionPickerDialog"
import { SelectedQuestionRow } from "@/components/assignments/SelectedQuestionRow"
import { Button } from "@/components/ui/button"
import { Calendar } from "@/components/ui/calendar"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { Textarea } from "@/components/ui/textarea"
import { ApiError } from "@/lib/api/client"
import { mapAssignmentFieldErrors } from "@/lib/api/field-errors"
import {
  fetchClasses,
  formatClassLabel,
  type ClassOption,
} from "@/lib/api/classes"
import {
  computeAssignmentTotalScore,
  defaultAssignmentFormValues,
  formatDeadlineDisplay,
  type AssignmentFormPayload,
  type SelectedQuestionFormValue,
} from "@/lib/mock-assignments"
import { cn } from "@/lib/utils"

interface AssignmentFormProps {
  initialValues?: AssignmentFormPayload
  onSubmit: (values: AssignmentFormPayload) => Promise<void>
  submitting?: boolean
}

type FieldErrors = Partial<Record<keyof AssignmentFormPayload, string>>

function parseDeadline(iso: string): { date: Date | undefined; time: string } {
  if (!iso) {
    return { date: undefined, time: "23:59" }
  }
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) {
    return { date: undefined, time: "23:59" }
  }
  const pad = (value: number) => String(value).padStart(2, "0")
  return {
    date,
    time: `${pad(date.getHours())}:${pad(date.getMinutes())}`,
  }
}

function toLocalIsoSeconds(date: Date): string {
  const pad = (value: number) => String(value).padStart(2, "0")
  return (
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}` +
    `T${pad(date.getHours())}:${pad(date.getMinutes())}:00`
  )
}

function combineDeadline(date: Date | undefined, time: string): string {
  if (!date) {
    return ""
  }
  const [hours, minutes] = time.split(":").map(Number)
  const combined = new Date(date)
  combined.setHours(Number.isFinite(hours) ? hours : 23, Number.isFinite(minutes) ? minutes : 59, 0, 0)
  return toLocalIsoSeconds(combined)
}

function validateForm(values: AssignmentFormPayload, t: TFunction): {
  fieldErrors: FieldErrors
  questionsError?: string
} {
  const fieldErrors: FieldErrors = {}

  if (!values.title.trim()) {
    fieldErrors.title = t("assignments:form.titleRequired")
  }
  if (values.classId == null) {
    fieldErrors.classId = t("assignments:form.classRequired")
  }
  if (!values.deadlineTime) {
    fieldErrors.deadlineTime = t("assignments:form.deadlineRequired")
  }

  if (values.questions.length === 0) {
    return {
      fieldErrors,
      questionsError: t("assignments:form.minQuestions"),
    }
  }

  return { fieldErrors }
}

export function AssignmentForm({
  initialValues = defaultAssignmentFormValues,
  onSubmit,
  submitting = false,
}: AssignmentFormProps) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const dragIndexRef = useRef<number | null>(null)

  const initialDeadline = parseDeadline(initialValues.deadlineTime)

  const [values, setValues] = useState<AssignmentFormPayload>(initialValues)
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({})
  const [questionsError, setQuestionsError] = useState<string>()
  const [questionRowErrors, setQuestionRowErrors] = useState<Record<number, string>>({})
  const [submitError, setSubmitError] = useState<string>()
  const [pickerOpen, setPickerOpen] = useState(false)
  const [draggingIndex, setDraggingIndex] = useState<number | null>(null)
  const [deadlineDate, setDeadlineDate] = useState<Date | undefined>(initialDeadline.date)
  const [deadlineTime, setDeadlineTime] = useState(initialDeadline.time)
  const [calendarOpen, setCalendarOpen] = useState(false)
  const [classes, setClasses] = useState<ClassOption[]>([])
  const [classesLoading, setClassesLoading] = useState(true)
  const [classesError, setClassesError] = useState<string | null>(null)
  const [classesReloadKey, setClassesReloadKey] = useState(0)

  const totalScore = computeAssignmentTotalScore(values.questions)

  useEffect(() => {
    const controller = new AbortController()

    void (async () => {
      setClassesLoading(true)
      setClassesError(null)
      try {
        const classList = await fetchClasses(controller.signal)
        if (controller.signal.aborted) {
          return
        }
        setClasses(classList)
      } catch (err) {
        if (controller.signal.aborted || (err instanceof DOMException && err.name === "AbortError")) {
          return
        }
        if (err instanceof ApiError) {
          setClassesError(err.message)
        } else {
          setClassesError(t("assignments:form.classesFailed"))
        }
        setClasses([])
      } finally {
        if (!controller.signal.aborted) {
          setClassesLoading(false)
        }
      }
    })()

    return () => controller.abort()
  }, [classesReloadKey])

  const missingClassId =
    values.classId != null && !classes.some((item) => item.id === values.classId)
      ? values.classId
      : null

  function updateField<K extends keyof AssignmentFormPayload>(
    field: K,
    fieldValue: AssignmentFormPayload[K]
  ) {
    setValues((current) => ({ ...current, [field]: fieldValue }))
    setFieldErrors((current) => ({ ...current, [field]: undefined }))
  }

  function updateDeadline(date: Date | undefined, time: string) {
    setDeadlineDate(date)
    setDeadlineTime(time)
    updateField("deadlineTime", combineDeadline(date, time))
  }

  function updateQuestion(index: number, nextValue: SelectedQuestionFormValue) {
    setValues((current) => ({
      ...current,
      questions: current.questions.map((item, itemIndex) =>
        itemIndex === index ? { ...nextValue, sortOrder: itemIndex } : item
      ),
    }))
    setQuestionsError(undefined)
  }

  function addQuestions(additions: SelectedQuestionFormValue[]) {
    setValues((current) => {
      const existingIds = new Set(current.questions.map((item) => item.questionId))
      const filtered = additions.filter((item) => !existingIds.has(item.questionId))
      const nextQuestions = [...current.questions, ...filtered].map((item, index) => ({
        ...item,
        sortOrder: index,
      }))
      return { ...current, questions: nextQuestions }
    })
    setQuestionsError(undefined)
  }

  function removeQuestion(index: number) {
    setValues((current) => ({
      ...current,
      questions: current.questions
        .filter((_, itemIndex) => itemIndex !== index)
        .map((item, itemIndex) => ({ ...item, sortOrder: itemIndex })),
    }))
    setQuestionRowErrors({})
  }

  function reorderQuestions(fromIndex: number, toIndex: number) {
    if (fromIndex === toIndex) {
      return
    }

    setValues((current) => {
      const next = [...current.questions]
      const [moved] = next.splice(fromIndex, 1)
      next.splice(toIndex, 0, moved)
      return {
        ...current,
        questions: next.map((item, index) => ({ ...item, sortOrder: index })),
      }
    })
    setQuestionRowErrors({})
  }

  function applySubmitError(error: unknown) {
    if (error instanceof ApiError) {
      if (error.fieldErrors?.length) {
        const mapped = mapAssignmentFieldErrors(error.fieldErrors)
        setFieldErrors(mapped.formErrors as FieldErrors)
        setQuestionRowErrors(mapped.questionRowErrors)
        setQuestionsError(mapped.questionsError)
        if (mapped.unmapped.length > 0) {
          setSubmitError(mapped.unmapped.join(t("listSep")))
        }
        return
      }
      setSubmitError(error.message)
      return
    }
    setSubmitError(t("assignments:form.saveFailed"))
  }

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault()

    setSubmitError(undefined)
    setFieldErrors({})
    setQuestionsError(undefined)
    setQuestionRowErrors({})

    const validation = validateForm(values, t)
    setFieldErrors(validation.fieldErrors)
    setQuestionsError(validation.questionsError)

    if (Object.keys(validation.fieldErrors).length > 0 || validation.questionsError) {
      return
    }

    const deletedRowErrors: Record<number, string> = {}
    values.questions.forEach((row, index) => {
      if (row.questionDeleted) {
        deletedRowErrors[index] = t("assignments:form.deletedQuestion")
      }
    })

    if (Object.keys(deletedRowErrors).length > 0) {
      setQuestionRowErrors(deletedRowErrors)
      return
    }

    try {
      await onSubmit({
        ...values,
        title: values.title.trim(),
        description: values.description.trim(),
        questions: values.questions.map((item, index) => ({
          ...item,
          sortOrder: index,
        })),
      })
    } catch (error) {
      applySubmitError(error)
    }
  }

  const deadlineLabel = values.deadlineTime
    ? formatDeadlineDisplay(values.deadlineTime)
    : t("assignments:form.pickDateTime")

  return (
    <form onSubmit={(event) => void handleSubmit(event)} className="mx-auto max-w-4xl space-y-8">
      <section className="space-y-6">
        <h2 className="text-lg font-semibold">{t("assignments:form.infoHeading")}</h2>

        <div className="space-y-2">
          <Label htmlFor="assignment-title">{t("assignments:form.title")}</Label>
          <Input
            id="assignment-title"
            value={values.title}
            onChange={(event) => updateField("title", event.target.value)}
            aria-invalid={Boolean(fieldErrors.title)}
          />
          {fieldErrors.title && (
            <p className="text-sm text-destructive">{fieldErrors.title}</p>
          )}
        </div>

        <div className="space-y-2">
          <Label htmlFor="assignment-description">{t("assignments:form.description")}</Label>
          <Textarea
            id="assignment-description"
            value={values.description}
            onChange={(event) => updateField("description", event.target.value)}
            className="min-h-24"
            placeholder={t("assignments:form.descriptionPlaceholder")}
          />
        </div>

        <div className="grid gap-6 md:grid-cols-2">
          <div className="space-y-2">
            <Label>{t("assignments:form.class")}</Label>
            <Select
              value={values.classId != null ? String(values.classId) : ""}
              onValueChange={(nextValue) => updateField("classId", Number(nextValue))}
              disabled={classesLoading || classesError != null}
            >
              <SelectTrigger className="w-full" aria-invalid={Boolean(fieldErrors.classId)}>
                <SelectValue
                  placeholder={
                    classesLoading
                      ? t("assignments:form.loadingClasses")
                      : classesError
                        ? t("assignments:form.classesLoadFailed")
                        : t("assignments:form.selectClass")
                  }
                />
              </SelectTrigger>
              <SelectContent>
                {missingClassId != null && (
                  <SelectItem value={String(missingClassId)} disabled>
                    {t("unknownClassId", { id: missingClassId })}
                  </SelectItem>
                )}
                {classes.map((item) => (
                  <SelectItem key={item.id} value={String(item.id)}>
                    {formatClassLabel(item)}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            {classesError && (
              <div className="flex items-center gap-2">
                <p className="text-sm text-destructive">{classesError}</p>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => setClassesReloadKey((key) => key + 1)}
                >
                  {t("retry")}
                </Button>
              </div>
            )}
            {fieldErrors.classId && (
              <p className="text-sm text-destructive">{fieldErrors.classId}</p>
            )}
          </div>

          <div className="space-y-2">
            <Label>{t("assignments:form.deadline")}</Label>
            <Popover open={calendarOpen} onOpenChange={setCalendarOpen}>
              <PopoverTrigger
                render={
                  <Button
                    type="button"
                    variant="outline"
                    className={cn(
                      "w-full justify-start text-left font-normal",
                      !values.deadlineTime && "text-muted-foreground"
                    )}
                    aria-invalid={Boolean(fieldErrors.deadlineTime)}
                  />
                }
              >
                <CalendarIcon className="size-4" />
                {deadlineLabel}
              </PopoverTrigger>
              <PopoverContent className="w-auto p-0" align="start">
                <Calendar
                  mode="single"
                  selected={deadlineDate}
                  onSelect={(date) => updateDeadline(date, deadlineTime)}
                  locale={getLocale() === "en-US" ? enUS : zhCN}
                />
                <div className="border-t p-3">
                  <Label htmlFor="deadline-time" className="text-xs">
                    {t("assignments:form.time")}
                  </Label>
                  <Input
                    id="deadline-time"
                    type="time"
                    value={deadlineTime}
                    onChange={(event) => updateDeadline(deadlineDate, event.target.value)}
                    className="mt-1"
                  />
                </div>
              </PopoverContent>
            </Popover>
            {fieldErrors.deadlineTime && (
              <p className="text-sm text-destructive">{fieldErrors.deadlineTime}</p>
            )}
          </div>
        </div>
      </section>

      <section className="space-y-4">
        <div className="flex items-center justify-between gap-4">
          <h2 className="text-lg font-semibold">{t("assignments:form.questionList")}</h2>
          <Button type="button" variant="outline" onClick={() => setPickerOpen(true)}>
            {t("assignments:form.addQuestion")}
          </Button>
        </div>

        {(values.submittedStudentCount ?? 0) > 0 && (
          <div className="rounded-md border bg-muted/40 px-4 py-3 text-sm text-muted-foreground">
            {t("assignments:form.submissionsLock", { count: values.submittedStudentCount ?? 0 })}
          </div>
        )}

        {questionsError && (
          <p className="text-sm text-destructive">{questionsError}</p>
        )}

        <div className="space-y-2">
          <Label>{t("assignments:form.totalScore")}</Label>
          <Input value={totalScore} readOnly disabled className="max-w-32" />
        </div>

        <div className="space-y-3">
          {values.questions.map((question, index) => (
            <SelectedQuestionRow
              key={`${question.id ?? "new"}-${question.questionId}-${index}`}
              index={index}
              value={question}
              error={questionRowErrors[index]}
              onChange={(nextValue) => updateQuestion(index, nextValue)}
              onRemove={() => removeQuestion(index)}
              onDragStart={() => {
                dragIndexRef.current = index
                setDraggingIndex(index)
              }}
              onDragOver={(event) => event.preventDefault()}
              onDrop={() => {
                if (dragIndexRef.current !== null) {
                  reorderQuestions(dragIndexRef.current, index)
                }
                dragIndexRef.current = null
                setDraggingIndex(null)
              }}
              isDragging={draggingIndex === index}
            />
          ))}
        </div>
      </section>

      <div className="flex flex-col items-end gap-2 border-t pt-6">
        {submitError && (
          <p className="text-sm text-destructive">{submitError}</p>
        )}
        <div className="flex justify-end gap-2">
          <Button
            type="button"
            variant="outline"
            onClick={() => navigate("/teacher/assignments")}
            disabled={submitting}
          >
            {t("cancel")}
          </Button>
          <Button type="submit" disabled={submitting}>
            {submitting ? t("saving") : t("save")}
          </Button>
        </div>
      </div>

      <QuestionPickerDialog
        open={pickerOpen}
        onOpenChange={setPickerOpen}
        excludedQuestionIds={values.questions.map((item) => item.questionId)}
        onConfirm={addQuestions}
      />
    </form>
  )
}
