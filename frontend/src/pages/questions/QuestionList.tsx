import { useCallback, useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import { Link, useNavigate, useSearchParams } from "react-router-dom"
import { Eye, Pencil, Search, Trash2, X } from "lucide-react"

import { ConfirmDeleteDialog } from "@/components/ConfirmDeleteDialog"
import { Badge } from "@/components/ui/badge"
import { Button, buttonVariants } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { fetchQuestions, softDeleteQuestion } from "@/lib/api/questions"
import { ApiError } from "@/lib/api/client"
import {
  DIFFICULTY_BADGE_CLASS,
  PAGE_SIZE,
  type PaginatedQuestions,
  type Question,
} from "@/lib/mock-questions"
import { cn } from "@/lib/utils"

export function QuestionList() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()

  const page = Math.max(1, Number(searchParams.get("page") ?? "1") || 1)

  const [inputValue, setInputValue] = useState("")
  const [searchTitle, setSearchTitle] = useState("")
  const [data, setData] = useState<PaginatedQuestions | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<Question | null>(null)
  const [deleting, setDeleting] = useState(false)
  const [deleteError, setDeleteError] = useState<string | null>(null)

  const loadQuestions = useCallback(async (signal?: AbortSignal) => {
    setLoading(true)
    setError(null)
    try {
      const result = await fetchQuestions(
        {
          page,
          pageSize: PAGE_SIZE,
          title: searchTitle,
        },
        signal,
      )

      if (signal?.aborted) {
        return
      }

      if (result.items.length === 0 && result.total > 0 && page > 1) {
        setSearchParams({ page: "1" })
        return
      }

      setData(result)
    } catch (err) {
      if (signal?.aborted || (err instanceof DOMException && err.name === "AbortError")) {
        return
      }
      if (err instanceof ApiError) {
        setError(err.message)
      } else {
        setError(t("questions:list.loadFailed"))
      }
      setData(null)
    } finally {
      if (!signal?.aborted) {
        setLoading(false)
      }
    }
  }, [page, searchTitle, setSearchParams, t])

  useEffect(() => {
    const controller = new AbortController()
    void loadQuestions(controller.signal)
    return () => controller.abort()
  }, [loadQuestions])

  function handleSearch() {
    setSearchTitle(inputValue.trim())
    setSearchParams({ page: "1" })
  }

  function handleReset() {
    setInputValue("")
    setSearchTitle("")
    navigate("/teacher/questions", { replace: true })
  }

  function goToPage(nextPage: number) {
    setSearchParams({ page: String(nextPage) })
  }

  async function handleConfirmDelete() {
    if (!deleteTarget) {
      return
    }

    setDeleting(true)
    setDeleteError(null)
    try {
      await softDeleteQuestion(deleteTarget.id)
      setDeleteTarget(null)
      await loadQuestions()
    } catch (err) {
      if (err instanceof ApiError) {
        setDeleteError(err.message)
      } else {
        setDeleteError(t("questions:list.deleteFailed"))
      }
    } finally {
      setDeleting(false)
    }
  }

  const isEmpty = !loading && !error && data?.total === 0
  const isSearchEmpty = isEmpty && searchTitle.length > 0
  const isNoData = isEmpty && searchTitle.length === 0
  const pageNumbers = data ? Array.from({ length: data.totalPages }, (_, index) => index + 1) : []

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div className="flex items-center gap-2">
          <div className="relative w-[280px]">
            <Search className="pointer-events-none absolute top-1/2 left-2.5 size-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              value={inputValue}
              onChange={(event) => setInputValue(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === "Enter") {
                  handleSearch()
                }
              }}
              placeholder={t("questions:list.searchPlaceholder")}
              autoComplete="off"
              className="pl-8"
            />
          </div>
          <Button type="button" variant="outline" onClick={handleSearch}>
            {t("search")}
          </Button>
          <Button type="button" variant="outline" onClick={handleReset}>
            {t("reset")}
          </Button>
        </div>

        <Link to="/teacher/questions/new" className={cn(buttonVariants())}>
          {t("questions:list.create")}
        </Link>
      </div>

      {loading ? (
        <div className="py-16 text-center text-muted-foreground">{t("loading")}</div>
      ) : error ? (
        <div className="flex flex-col items-center justify-center gap-4 py-16 text-center">
          <p className="text-muted-foreground">{error}</p>
          <Button type="button" variant="outline" onClick={() => void loadQuestions()}>
            {t("retry")}
          </Button>
        </div>
      ) : isNoData ? (
        <div className="flex flex-col items-center justify-center gap-4 py-16 text-center">
          <p className="text-muted-foreground">{t("questions:list.empty")}</p>
          <Link to="/teacher/questions/new" className={cn(buttonVariants())}>
            {t("questions:list.create")}
          </Link>
        </div>
      ) : isSearchEmpty ? (
        <div className="py-16 text-center text-muted-foreground">{t("questions:list.noMatch")}</div>
      ) : (
        <>
          {deleteError && (
            <div
              role="alert"
              className="flex items-center justify-between gap-4 rounded-md border border-destructive/50 bg-destructive/10 px-4 py-3 text-sm text-destructive"
            >
              <p>{deleteError}</p>
              <Button
                type="button"
                variant="ghost"
                size="icon-sm"
                aria-label={t("close")}
                className="shrink-0 text-destructive hover:text-destructive"
                onClick={() => setDeleteError(null)}
              >
                <X className="size-4" />
              </Button>
            </div>
          )}

          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t("questions:list.title")}</TableHead>
                <TableHead>{t("questions:list.type")}</TableHead>
                <TableHead>{t("questions:list.difficulty")}</TableHead>
                <TableHead>{t("questions:list.subQuestionCount")}</TableHead>
                <TableHead>{t("questions:list.totalScore")}</TableHead>
                <TableHead>{t("questions:list.submitCount")}</TableHead>
                <TableHead className="w-[120px]" />
              </TableRow>
            </TableHeader>
            <TableBody>
              {data?.items.map((question) => (
                <TableRow key={question.id}>
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
                  <TableCell>{question.submitNum}</TableCell>
                  <TableCell>
                    <div className="flex items-center gap-1">
                      <Button
                        type="button"
                        variant="ghost"
                        size="icon-sm"
                        aria-label={t("viewAria")}
                        onClick={() => navigate(`/teacher/questions/${question.id}/preview`)}
                      >
                        <Eye className="size-4" />
                      </Button>
                      <Button
                        type="button"
                        variant="ghost"
                        size="icon-sm"
                        aria-label={t("editAria")}
                        onClick={() => navigate(`/teacher/questions/${question.id}/edit`)}
                      >
                        <Pencil className="size-4" />
                      </Button>
                      <Button
                        type="button"
                        variant="ghost"
                        size="icon-sm"
                        aria-label={t("deleteAria")}
                        onClick={() => setDeleteTarget(question)}
                      >
                        <Trash2 className="size-4 text-destructive" />
                      </Button>
                    </div>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>

          {data && (
            <div className="flex flex-wrap items-center justify-between gap-4">
              <p className="text-sm text-muted-foreground">
                {t("questions:list.pageSummary", { total: data.total, page: data.page, totalPages: data.totalPages })}
              </p>
              <div className="flex flex-wrap items-center gap-1">
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  disabled={data.page <= 1}
                  onClick={() => goToPage(data.page - 1)}
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
                    onClick={() => goToPage(pageNumber)}
                  >
                    {pageNumber}
                  </Button>
                ))}
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  disabled={data.page >= data.totalPages}
                  onClick={() => goToPage(data.page + 1)}
                >
                  {t("nextPage")}
                </Button>
              </div>
            </div>
          )}
        </>
      )}

      <ConfirmDeleteDialog
        open={Boolean(deleteTarget)}
        onOpenChange={(open) => {
          if (!open && !deleting) {
            setDeleteTarget(null)
          }
        }}
        itemTitle={deleteTarget?.title ?? ""}
        onConfirm={handleConfirmDelete}
        loading={deleting}
      />
    </div>
  )
}
