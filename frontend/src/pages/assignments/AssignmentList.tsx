import { useCallback, useEffect, useMemo, useState } from "react"
import { useTranslation } from "react-i18next"
import { Link, useNavigate, useSearchParams } from "react-router-dom"
import { Pencil, Search, Trash2, X } from "lucide-react"

import { CannotDeletePublishedDialog } from "@/components/assignments/CannotDeletePublishedDialog"
import { ConfirmUnpublishDialog } from "@/components/assignments/ConfirmUnpublishDialog"
import { ConfirmDeleteDialog } from "@/components/ConfirmDeleteDialog"
import { Badge } from "@/components/ui/badge"
import { Button, buttonVariants } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from "@/components/ui/tooltip"
import { ApiError } from "@/lib/api/client"
import {
  buildClassNameMap,
  fetchClasses,
  formatClassLabel,
  type ClassOption,
} from "@/lib/api/classes"
import {
  deleteExperiment,
  fetchExperiments,
  updateExperimentStatus,
  type ExperimentListItem,
  type PaginatedExperimentList,
} from "@/lib/api/experiments"
import {
  EXPERIMENT_STATUSES,
  formatDeadlineDisplay,
  isDueSoon,
  PAGE_SIZE,
  STATUS_BADGE_CLASS,
  type ExperimentStatus,
} from "@/lib/mock-assignments"
import { cn } from "@/lib/utils"

export function AssignmentList() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()

  const page = Math.max(1, Number(searchParams.get("page") ?? "1") || 1)

  const [inputValue, setInputValue] = useState("")
  const [searchTitle, setSearchTitle] = useState("")
  const [classFilter, setClassFilter] = useState<string>("all")
  const [statusFilter, setStatusFilter] = useState<string>("all")
  const [data, setData] = useState<PaginatedExperimentList | null>(null)
  const [classes, setClasses] = useState<ClassOption[]>([])
  const [classesError, setClassesError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [reloadKey, setReloadKey] = useState(0)
  const [deleteTarget, setDeleteTarget] = useState<ExperimentListItem | null>(null)
  const [blockedDeleteTarget, setBlockedDeleteTarget] = useState<ExperimentListItem | null>(null)
  const [unpublishTarget, setUnpublishTarget] = useState<ExperimentListItem | null>(null)
  const [deleting, setDeleting] = useState(false)
  const [deleteError, setDeleteError] = useState<string | null>(null)
  const [statusUpdating, setStatusUpdating] = useState(false)
  const [statusError, setStatusError] = useState<string | null>(null)

  const classId = classFilter === "all" ? null : Number(classFilter)
  const parsedStatus = statusFilter === "all" ? null : Number(statusFilter)
  const status: ExperimentStatus | null =
    parsedStatus === 0 || parsedStatus === 1 || parsedStatus === 2 ? parsedStatus : null

  const loadClasses = useCallback(async (signal?: AbortSignal) => {
    setClassesError(null)
    try {
      const classList = await fetchClasses(signal)
      if (signal?.aborted) {
        return
      }
      setClasses(classList)
    } catch (err) {
      if (signal?.aborted || (err instanceof DOMException && err.name === "AbortError")) {
        return
      }
      setClasses([])
      setClassesError(t("assignments:list.classesFailed"))
    }
  }, [t])

  const loadExperiments = useCallback(
    async (signal?: AbortSignal) => {
      setLoading(true)
      setError(null)
      try {
        const result = await fetchExperiments(
          {
            page,
            pageSize: PAGE_SIZE,
            title: searchTitle,
            classId,
            status,
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
          setError(t("assignments:list.loadFailed"))
        }
        setData(null)
      } finally {
        if (!signal?.aborted) {
          setLoading(false)
        }
      }
    },
    [page, searchTitle, classId, status, setSearchParams, t],
  )

  useEffect(() => {
    const controller = new AbortController()
    void loadClasses(controller.signal)
    return () => controller.abort()
  }, [loadClasses, reloadKey])

  useEffect(() => {
    const controller = new AbortController()
    void loadExperiments(controller.signal)
    return () => controller.abort()
  }, [loadExperiments, reloadKey])

  const classNameMap = useMemo(() => buildClassNameMap(classes), [classes])

  const displayItems = useMemo(() => data?.items ?? [], [data])

  function handleSearch() {
    setSearchTitle(inputValue.trim())
    setSearchParams({ page: "1" })
  }

  function handleReset() {
    setInputValue("")
    setSearchTitle("")
    setClassFilter("all")
    setStatusFilter("all")
    navigate("/teacher/assignments", { replace: true })
  }

  function handleClassFilterChange(nextValue: string | null) {
    setClassFilter(nextValue ?? "all")
    setSearchParams({ page: "1" })
  }

  function handleStatusFilterChange(nextValue: string | null) {
    setStatusFilter(nextValue ?? "all")
    setSearchParams({ page: "1" })
  }

  function goToPage(nextPage: number) {
    setSearchParams({ page: String(nextPage) })
  }

  function handleRetry() {
    setReloadKey((key) => key + 1)
  }

  async function handleConfirmDelete() {
    if (!deleteTarget || deleteTarget.status === 1) {
      return
    }

    setDeleting(true)
    setDeleteError(null)
    try {
      await deleteExperiment(deleteTarget.id)
      setDeleteTarget(null)
      await loadExperiments()
    } catch (err) {
      if (err instanceof ApiError) {
        setDeleteError(err.message)
      } else {
        setDeleteError(t("assignments:list.deleteFailed"))
      }
    } finally {
      setDeleting(false)
    }
  }

  function handleGoUnpublishFromBlockedDelete() {
    if (!blockedDeleteTarget) {
      return
    }
    setUnpublishTarget(blockedDeleteTarget)
    setBlockedDeleteTarget(null)
  }

  async function applyStatusError(err: unknown) {
    if (err instanceof ApiError && err.status === 404) {
      setUnpublishTarget(null)
      setStatusError(t("assignments:list.deleted"))
      await loadExperiments()
      return
    }

    const fieldMessage =
      err instanceof ApiError
        ? err.fieldErrors?.find((item) => item.field === "status")?.message
        : undefined

    if (fieldMessage === "invalid status transition") {
      setUnpublishTarget(null)
      setStatusError(t("assignments:list.statusChanged"))
      await loadExperiments()
      return
    }

    if (fieldMessage === "cannot publish an assignment with no questions") {
      setStatusError(t("assignments:list.noQuestions"))
      return
    }

    if (err instanceof ApiError) {
      setStatusError(err.message)
      return
    }

    setStatusError(t("assignments:list.statusFailed"))
  }

  async function handleConfirmWithdraw() {
    if (!unpublishTarget) {
      return
    }
    if (unpublishTarget.status !== 1 && unpublishTarget.status !== 2) {
      return
    }

    setStatusUpdating(true)
    setStatusError(null)
    try {
      await updateExperimentStatus(unpublishTarget.id, 0)
      setUnpublishTarget(null)
      await loadExperiments()
    } catch (err) {
      await applyStatusError(err)
    } finally {
      setStatusUpdating(false)
    }
  }

  async function handlePublish(experiment: ExperimentListItem) {
    if (experiment.status !== 0) {
      return
    }

    setStatusUpdating(true)
    setStatusError(null)
    try {
      await updateExperimentStatus(experiment.id, 1)
      await loadExperiments()
    } catch (err) {
      await applyStatusError(err)
    } finally {
      setStatusUpdating(false)
    }
  }

  const isEmpty = !loading && !error && data?.total === 0
  const isSearchEmpty = isEmpty && searchTitle.length > 0
  const isClassFilterEmpty =
    isEmpty &&
    classFilter !== "all" &&
    statusFilter === "all" &&
    searchTitle.length === 0
  const isStatusFilterEmpty =
    isEmpty &&
    statusFilter !== "all" &&
    classFilter === "all" &&
    searchTitle.length === 0
  const isCombinedFilterEmpty =
    isEmpty &&
    classFilter !== "all" &&
    statusFilter !== "all" &&
    searchTitle.length === 0
  const isNoData =
    isEmpty &&
    searchTitle.length === 0 &&
    classFilter === "all" &&
    statusFilter === "all"
  const pageNumbers = data ? Array.from({ length: data.totalPages }, (_, index) => index + 1) : []

  function renderClassCell(experiment: ExperimentListItem) {
    if (experiment.classId === null) {
      return t("noClassAssigned")
    }
    return classNameMap.get(experiment.classId) ?? t("unknownClass")
  }

  function renderStatusButton(experiment: ExperimentListItem) {
    if (experiment.status === 1 || experiment.status === 2) {
      return (
        <Button
          type="button"
          variant="outline"
          size="sm"
          className="text-destructive hover:text-destructive"
          disabled={statusUpdating}
          onClick={() => setUnpublishTarget(experiment)}
        >
          {t("assignments:list.unpublish")}
        </Button>
      )
    }

    if (experiment.status !== 0) {
      return null
    }

    const canPublish =
      experiment.questionCount === null || experiment.questionCount > 0
    const button = (
      <Button
        type="button"
        size="sm"
        disabled={statusUpdating || !canPublish}
        onClick={() => void handlePublish(experiment)}
      >
        {t("assignments:list.publish")}
      </Button>
    )

    if (!canPublish) {
      return (
        <Tooltip>
          <TooltipTrigger
            render={
              <span className="inline-flex">
                {button}
              </span>
            }
          />
          <TooltipContent>{t("assignments:list.noQuestionsTooltip")}</TooltipContent>
        </Tooltip>
      )
    }

    return button
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div className="flex flex-wrap items-center gap-2">
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
              placeholder={t("assignments:list.searchPlaceholder")}
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
          <Select value={classFilter} onValueChange={handleClassFilterChange}>
            <SelectTrigger className="w-[180px]">
              <SelectValue placeholder={t("allClasses")} />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">{t("allClasses")}</SelectItem>
              {classes.map((item) => (
                <SelectItem key={item.id} value={String(item.id)}>
                  {formatClassLabel(item)}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Select value={statusFilter} onValueChange={handleStatusFilterChange}>
            <SelectTrigger className="w-[140px]">
              <SelectValue placeholder={t("allStatuses")} />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">{t("allStatuses")}</SelectItem>
              {EXPERIMENT_STATUSES.map((key) => (
                <SelectItem key={key} value={String(key)}>
                  {t(`assignments:status.${key}`)}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        <Link to="/teacher/assignments/new" className={cn(buttonVariants())}>
          {t("assignments:list.create")}
        </Link>
      </div>

      {loading ? (
        <div className="py-16 text-center text-muted-foreground">{t("loading")}</div>
      ) : error ? (
        <div className="flex flex-col items-center justify-center gap-4 py-16 text-center">
          <p className="text-destructive">{error}</p>
          <Button type="button" variant="outline" onClick={handleRetry}>
            {t("retry")}
          </Button>
        </div>
      ) : isNoData ? (
        <div className="flex flex-col items-center justify-center gap-4 py-16 text-center">
          <p className="text-muted-foreground">{t("assignments:list.empty")}</p>
          <Link to="/teacher/assignments/new" className={cn(buttonVariants())}>
            {t("assignments:list.create")}
          </Link>
        </div>
      ) : isClassFilterEmpty ? (
        <div className="py-16 text-center text-muted-foreground">{t("assignments:list.emptyClass")}</div>
      ) : isStatusFilterEmpty ? (
        <div className="py-16 text-center text-muted-foreground">{t("assignments:list.emptyStatus")}</div>
      ) : isCombinedFilterEmpty ? (
        <div className="py-16 text-center text-muted-foreground">{t("assignments:list.noMatch")}</div>
      ) : isSearchEmpty ? (
        <div className="py-16 text-center text-muted-foreground">{t("assignments:list.noMatch")}</div>
      ) : (
        <>
          {classesError && (
            <p className="text-sm text-muted-foreground">{classesError}</p>
          )}

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

          {statusError && (
            <div
              role="alert"
              className="flex items-center justify-between gap-4 rounded-md border border-destructive/50 bg-destructive/10 px-4 py-3 text-sm text-destructive"
            >
              <p>{statusError}</p>
              <Button
                type="button"
                variant="ghost"
                size="icon-sm"
                aria-label={t("close")}
                className="shrink-0 text-destructive hover:text-destructive"
                onClick={() => setStatusError(null)}
              >
                <X className="size-4" />
              </Button>
            </div>
          )}

          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t("assignments:list.title")}</TableHead>
                <TableHead>{t("assignments:list.class")}</TableHead>
                <TableHead>{t("assignments:list.status")}</TableHead>
                <TableHead>{t("assignments:list.deadline")}</TableHead>
                <TableHead className="w-[200px]" />
              </TableRow>
            </TableHeader>
            <TableBody>
              {displayItems.map((experiment) => (
                <TableRow key={experiment.id}>
                  <TableCell className="max-w-xs truncate font-medium">
                    {experiment.title || t("unnamedAssignment")}
                  </TableCell>
                  <TableCell>{renderClassCell(experiment)}</TableCell>
                  <TableCell>
                    <Badge className={STATUS_BADGE_CLASS[experiment.status]}>
                      {t(`assignments:status.${experiment.status}`)}
                    </Badge>
                  </TableCell>
                  <TableCell
                    className={cn(
                      experiment.deadlineTime != null &&
                        isDueSoon(experiment.deadlineTime, experiment.status) &&
                        "font-medium text-amber-600 dark:text-amber-400",
                    )}
                  >
                    {experiment.deadlineTime == null
                      ? "—"
                      : formatDeadlineDisplay(experiment.deadlineTime)}
                  </TableCell>
                  <TableCell>
                    <div className="flex items-center gap-1">
                      {renderStatusButton(experiment)}
                      <Button
                        type="button"
                        variant="ghost"
                        size="icon-sm"
                        aria-label={t("editAria")}
                        onClick={() => navigate(`/teacher/assignments/${experiment.id}/edit`)}
                      >
                        <Pencil className="size-4" />
                      </Button>
                      <Button
                        type="button"
                        variant="ghost"
                        size="icon-sm"
                        aria-label={t("deleteAria")}
                        onClick={() => {
                          if (experiment.status === 1) {
                            setBlockedDeleteTarget(experiment)
                            return
                          }
                          setDeleteTarget(experiment)
                        }}
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
                {t("assignments:list.pageSummary", { total: data.total, page: data.page, totalPages: data.totalPages })}
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
        itemTitle={deleteTarget?.title || t("unnamedAssignment")}
        onConfirm={handleConfirmDelete}
        loading={deleting}
      />

      <CannotDeletePublishedDialog
        open={Boolean(blockedDeleteTarget)}
        onOpenChange={(open) => {
          if (!open) {
            setBlockedDeleteTarget(null)
          }
        }}
        itemTitle={blockedDeleteTarget?.title || t("unnamedAssignment")}
        onGoUnpublish={handleGoUnpublishFromBlockedDelete}
      />

      <ConfirmUnpublishDialog
        open={Boolean(unpublishTarget)}
        onOpenChange={(open) => {
          if (!open && !statusUpdating) {
            setUnpublishTarget(null)
          }
        }}
        itemTitle={unpublishTarget?.title || t("unnamedAssignment")}
        onConfirm={handleConfirmWithdraw}
        loading={statusUpdating}
      />
    </div>
  )
}
