import { useCallback, useEffect, useState } from "react"
import { Search } from "lucide-react"
import { useTranslation } from "react-i18next"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Checkbox } from "@/components/ui/checkbox"
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { ApiError } from "@/lib/api/client"
import { fetchQuestions } from "@/lib/api/questions"
import type { SelectedQuestionFormValue } from "@/lib/mock-assignments"
import {
  DIFFICULTY_BADGE_CLASS,
  PAGE_SIZE,
  type PaginatedQuestions,
  type Question,
} from "@/lib/mock-questions"
import { cn } from "@/lib/utils"

interface QuestionPickerDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  excludedQuestionIds: number[]
  onConfirm: (questions: SelectedQuestionFormValue[]) => void
}

export function QuestionPickerDialog({
  open,
  onOpenChange,
  excludedQuestionIds,
  onConfirm,
}: QuestionPickerDialogProps) {
  const { t } = useTranslation()
  const [inputValue, setInputValue] = useState("")
  const [searchTitle, setSearchTitle] = useState("")
  const [page, setPage] = useState(1)
  const [data, setData] = useState<PaginatedQuestions | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [selectedQuestions, setSelectedQuestions] = useState<Map<number, Question>>(new Map())

  const loadQuestions = useCallback(async (signal?: AbortSignal) => {
    setLoading(true)
    setError(null)
    try {
      const result = await fetchQuestions(
        { page, pageSize: PAGE_SIZE, title: searchTitle },
        signal,
      )

      if (signal?.aborted) {
        return
      }

      setData(result)
      if (result.page !== page) {
        setPage(result.page)
      }
    } catch (err) {
      if (signal?.aborted || (err instanceof DOMException && err.name === "AbortError")) {
        return
      }
      if (err instanceof ApiError) {
        setError(err.message)
      } else {
        setError(t("assignments:picker.loadFailed"))
      }
      setData(null)
    } finally {
      if (!signal?.aborted) {
        setLoading(false)
      }
    }
  }, [page, searchTitle, t])

  function handleOpenChange(nextOpen: boolean) {
    if (!nextOpen) {
      setInputValue("")
      setSearchTitle("")
      setPage(1)
      setError(null)
      setSelectedQuestions(new Map())
    }
    onOpenChange(nextOpen)
  }

  useEffect(() => {
    if (!open) {
      return
    }

    const controller = new AbortController()
    void loadQuestions(controller.signal)
    return () => controller.abort()
  }, [open, loadQuestions])

  function handleSearch() {
    setSearchTitle(inputValue.trim())
    setPage(1)
  }

  function toggleQuestion(question: Question) {
    if (excludedQuestionIds.includes(question.id)) {
      return
    }

    setSelectedQuestions((current) => {
      const next = new Map(current)
      if (next.has(question.id)) {
        next.delete(question.id)
      } else {
        next.set(question.id, question)
      }
      return next
    })
  }

  function handleConfirm() {
    const additions: SelectedQuestionFormValue[] = [...selectedQuestions.values()].map(
      (question, index) => ({
        questionId: question.id,
        title: question.title,
        subQuestionCount: question.subQuestions.length,
        questionTotalScore: question.totalScore,
        points: question.totalScore,
        pointsOverridden: false,
        sortOrder: index,
      })
    )

    onConfirm(additions)
    handleOpenChange(false)
  }

  const pageNumbers = data ? Array.from({ length: data.totalPages }, (_, index) => index + 1) : []

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="flex max-h-[85vh] flex-col sm:max-w-3xl">
        <DialogHeader>
          <DialogTitle>{t("assignments:picker.title")}</DialogTitle>
        </DialogHeader>

        <div className="flex items-center gap-2">
          <div className="relative flex-1">
            <Search className="pointer-events-none absolute top-1/2 left-2.5 size-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              value={inputValue}
              onChange={(event) => setInputValue(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === "Enter") {
                  handleSearch()
                }
              }}
              placeholder={t("assignments:picker.searchPlaceholder")}
              className="pl-8"
            />
          </div>
          <Button type="button" variant="outline" onClick={handleSearch}>
            {t("search")}
          </Button>
        </div>

        <div className="min-h-0 flex-1 overflow-y-auto">
          {loading ? (
            <p className="py-12 text-center text-muted-foreground">{t("loading")}</p>
          ) : error ? (
            <p className="py-12 text-center text-destructive">{error}</p>
          ) : data?.total === 0 ? (
            <p className="py-12 text-center text-muted-foreground">{t("assignments:picker.noMatch")}</p>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="w-10" />
                  <TableHead>{t("assignments:picker.headingTitle")}</TableHead>
                  <TableHead>{t("assignments:picker.type")}</TableHead>
                  <TableHead>{t("assignments:picker.difficulty")}</TableHead>
                  <TableHead>{t("assignments:picker.subQuestionCount")}</TableHead>
                  <TableHead>{t("assignments:picker.totalScore")}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {data?.items.map((question) => {
                  const isExcluded = excludedQuestionIds.includes(question.id)
                  const isSelected = selectedQuestions.has(question.id)

                  return (
                    <TableRow
                      key={question.id}
                      data-state={isSelected ? "selected" : undefined}
                      className={cn(isExcluded && "opacity-50")}
                    >
                      <TableCell>
                        <Checkbox
                          checked={isExcluded || isSelected}
                          disabled={isExcluded}
                          onCheckedChange={() => toggleQuestion(question)}
                          aria-label={t("selectQuestionAria", { title: question.title })}
                        />
                      </TableCell>
                      <TableCell className="max-w-xs truncate font-medium">
                        {question.title}
                      </TableCell>
                      <TableCell>{t(`questions:type.${question.type}`)}</TableCell>
                      <TableCell>
                        <Badge className={DIFFICULTY_BADGE_CLASS[question.difficulty]}>
                          {t(`questions:difficulty.${question.difficulty}`)}
                        </Badge>
                      </TableCell>
                      <TableCell>{question.subQuestions.length}</TableCell>
                      <TableCell>{question.totalScore}</TableCell>
                    </TableRow>
                  )
                })}
              </TableBody>
            </Table>
          )}
        </div>

        {data && data.total > 0 && (
          <div className="flex flex-wrap items-center justify-between gap-4">
            <p className="text-sm text-muted-foreground">
              {t("assignments:picker.pageSummary", { total: data.total, page: data.page, totalPages: data.totalPages })}
            </p>
            <div className="flex flex-wrap items-center gap-1">
              <Button
                type="button"
                variant="outline"
                size="sm"
                disabled={data.page <= 1}
                onClick={() => setPage(data.page - 1)}
              >
                {t("prevPage")}
              </Button>
              {pageNumbers.map((pageNumber) => (
                <Button
                  key={pageNumber}
                  type="button"
                  variant={pageNumber === data.page ? "secondary" : "outline"}
                  size="sm"
                  className={cn(pageNumber === data.page && "bg-muted")}
                  onClick={() => setPage(pageNumber)}
                >
                  {pageNumber}
                </Button>
              ))}
              <Button
                type="button"
                variant="outline"
                size="sm"
                disabled={data.page >= data.totalPages}
                onClick={() => setPage(data.page + 1)}
              >
                {t("nextPage")}
              </Button>
            </div>
          </div>
        )}

        <DialogFooter className="border-t-0 bg-transparent p-0 pt-2 sm:justify-end">
          <Button type="button" variant="outline" onClick={() => handleOpenChange(false)}>
            {t("cancel")}
          </Button>
          <Button
            type="button"
            disabled={selectedQuestions.size === 0}
            onClick={handleConfirm}
          >
            {t("assignments:picker.confirm", { count: selectedQuestions.size })}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
