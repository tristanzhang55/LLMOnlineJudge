import { useCallback, useEffect, useMemo, useState } from "react"
import { useTranslation } from "react-i18next"
import { Link, useSearchParams } from "react-router-dom"
import { Download, Search, X } from "lucide-react"

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
import { fetchClasses, formatClassLabel, type ClassOption } from "@/lib/api/classes"
import { ApiError } from "@/lib/api/client"
import { getExperimentById, type ExperimentListItem } from "@/lib/api/experiments"
import {
  classesInSemester,
  downloadGradeExport,
  fetchClassMajorByStudentId,
  fetchExperimentsForClass,
  fetchTeacherGrades,
  isGradeRoundStatus,
  pickDefaultClass,
  pickDefaultExperiment,
  pickLatest,
  SEMESTER_NONE,
  semesterKeyOf,
  semesterOptions,
  sortByRecency,
  type GradeStudentRow,
  type PaginatedGradeList,
} from "@/lib/api/teacher-grades"
import {
  ROUND_STATUS_BADGE_CLASS,
} from "@/lib/api/teacher-submissions"
import { PAGE_SIZE, STATUS_BADGE_CLASS } from "@/lib/mock-assignments"
import { cn } from "@/lib/utils"

export function Analytics() {
  const { t } = useTranslation()
  const [searchParams, setSearchParams] = useSearchParams()
  const page = Math.max(1, Number(searchParams.get("page") ?? "1") || 1)

  const [inputValue, setInputValue] = useState("")
  const [studentName, setStudentName] = useState("")
  const [classes, setClasses] = useState<ClassOption[]>([])
  const [classesError, setClassesError] = useState<string | null>(null)
  const [classesLoaded, setClassesLoaded] = useState(false)
  const [semesterKey, setSemesterKey] = useState("")
  const [classId, setClassId] = useState<number | null>(null)
  const [experimentId, setExperimentId] = useState<number | null>(null)
  const [experiments, setExperiments] = useState<ExperimentListItem[]>([])
  const [experimentsError, setExperimentsError] = useState<string | null>(null)
  const [cascadeNonce, setCascadeNonce] = useState(0)
  const [pickerReady, setPickerReady] = useState(false)
  const [data, setData] = useState<PaginatedGradeList | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [banner, setBanner] = useState<string | null>(null)
  const [forbidden, setForbidden] = useState(false)
  const [exporting, setExporting] = useState(false)
  const [gradesReloadKey, setGradesReloadKey] = useState(0)
  const [majorByStudentId, setMajorByStudentId] = useState<Map<string, string | null>>(new Map())
  const [overallScore, setOverallScore] = useState<number | null>(null)

  const semesters = useMemo(() => semesterOptions(classes), [classes])
  const classesForSemester = useMemo(
    () => sortByRecency(classesInSemester(classes, semesterKey)),
    [classes, semesterKey],
  )
  const canExport = classId != null && classId > 0 && experimentId != null && experimentId > 0

  function writePage(nextPage: number) {
    setSearchParams((current) => {
      const next = new URLSearchParams(current)
      if (nextPage <= 1) {
        next.delete("page")
      } else {
        next.set("page", String(nextPage))
      }
      return next
    })
  }

  function applyClassSelection(nextClassId: number | null, nextSemesterKey?: string) {
    if (nextSemesterKey != null) {
      setSemesterKey(nextSemesterKey)
    }
    setPickerReady(false)
    setExperimentId(null)
    setClassId(nextClassId)
    setCascadeNonce((key) => key + 1)
    writePage(1)
  }

  function applyDefaultChain(classList: ClassOption[]) {
    const picked = pickDefaultClass(classList)
    if (!picked) {
      setSemesterKey("")
      setClassId(null)
      setExperimentId(null)
      setExperiments([])
      return
    }
    applyClassSelection(picked.id, semesterKeyOf(picked))
  }

  const loadClasses = useCallback(async (signal?: AbortSignal) => {
    setClassesError(null)
    try {
      const classList = await fetchClasses(signal)
      if (signal?.aborted) {
        return
      }
      setClasses(classList)
      setClassesLoaded(true)
      const picked = pickDefaultClass(classList)
      if (!picked) {
        setSemesterKey("")
        setClassId(null)
        setExperimentId(null)
        setExperiments([])
        setPickerReady(true)
        return
      }
      setSemesterKey(semesterKeyOf(picked))
      setPickerReady(false)
      setExperimentId(null)
      setClassId(picked.id)
      setCascadeNonce((key) => key + 1)
    } catch (err) {
      if (signal?.aborted || (err instanceof DOMException && err.name === "AbortError")) {
        return
      }
      if (err instanceof ApiError && err.status === 403) {
        setForbidden(true)
        return
      }
      setClasses([])
      setClassesLoaded(true)
      setPickerReady(true)
      setClassesError(t("analytics:classesFailed"))
    }
  }, [])

  const loadExperiments = useCallback(
    async (signal?: AbortSignal) => {
      if (classId == null || classId < 1) {
        setExperiments([])
        setExperimentId(null)
        setExperimentsError(null)
        setPickerReady(true)
        return
      }
      setExperimentsError(null)
      try {
        const items = await fetchExperimentsForClass(classId, signal)
        if (signal?.aborted) {
          return
        }
        setExperiments(items)
        const picked = pickDefaultExperiment(items)
        setExperimentId(picked?.id ?? null)
        setPickerReady(true)
      } catch (err) {
        if (signal?.aborted || (err instanceof DOMException && err.name === "AbortError")) {
          return
        }
        if (err instanceof ApiError && err.status === 403) {
          setForbidden(true)
          return
        }
        setExperiments([])
        setExperimentId(null)
        setPickerReady(true)
        setExperimentsError(t("analytics:experimentsFailed"))
      }
    },
    [classId],
  )

  const loadMajors = useCallback(
    async (signal?: AbortSignal) => {
      if (classId == null || classId < 1) {
        setMajorByStudentId(new Map())
        return
      }
      try {
        const majors = await fetchClassMajorByStudentId(classId, signal)
        if (signal?.aborted) {
          return
        }
        setMajorByStudentId(majors)
      } catch (err) {
        if (signal?.aborted || (err instanceof DOMException && err.name === "AbortError")) {
          return
        }
        if (err instanceof ApiError && err.status === 403) {
          setForbidden(true)
          return
        }
        setMajorByStudentId(new Map())
      }
    },
    [classId],
  )

  const loadOverallScore = useCallback(
    async (signal?: AbortSignal) => {
      if (experimentId == null || experimentId < 1) {
        setOverallScore(null)
        return
      }
      setOverallScore(null)
      try {
        const detail = await getExperimentById(experimentId, signal)
        if (signal?.aborted) {
          return
        }
        if (!detail) {
          setOverallScore(null)
          return
        }
        const total = detail.questions
          .filter((question) => !question.questionDeleted)
          .reduce((sum, question) => sum + question.effectivePoints, 0)
        setOverallScore(total)
      } catch (err) {
        if (signal?.aborted || (err instanceof DOMException && err.name === "AbortError")) {
          return
        }
        setOverallScore(null)
      }
    },
    [experimentId],
  )

  const loadGrades = useCallback(
    async (signal?: AbortSignal) => {
      if (classId == null || classId < 1) {
        setData(null)
        setError(null)
        setLoading(false)
        return
      }
      if (!pickerReady) {
        setLoading(true)
        return
      }
      setLoading(true)
      setError(null)
      try {
        const result = await fetchTeacherGrades(
          {
            classId,
            experimentId,
            page,
            pageSize: PAGE_SIZE,
            studentName,
          },
          signal,
        )
        if (signal?.aborted) {
          return
        }
        if (result.items.length === 0 && result.total > 0 && page > 1) {
          setSearchParams((current) => {
            const next = new URLSearchParams(current)
            next.delete("page")
            return next
          })
          return
        }
        setData(result)
      } catch (err) {
        if (signal?.aborted || (err instanceof DOMException && err.name === "AbortError")) {
          return
        }
        if (err instanceof ApiError && err.status === 403) {
          setForbidden(true)
          return
        }
        if (err instanceof ApiError && err.status === 404 && experimentId != null) {
          setBanner(err.message || t("analytics:experimentMissing"))
          setExperimentId(null)
          setData(null)
          return
        }
        if (err instanceof ApiError) {
          setError(err.message)
        } else {
          setError(t("analytics:loadFailed"))
        }
        setData(null)
      } finally {
        if (!signal?.aborted) {
          setLoading(false)
        }
      }
    },
    [classId, experimentId, page, studentName, pickerReady, setSearchParams],
  )

  useEffect(() => {
    const controller = new AbortController()
    void loadClasses(controller.signal)
    return () => controller.abort()
  }, [loadClasses])

  useEffect(() => {
    const controller = new AbortController()
    void loadExperiments(controller.signal)
    return () => controller.abort()
  }, [loadExperiments, cascadeNonce])

  useEffect(() => {
    const controller = new AbortController()
    void loadMajors(controller.signal)
    return () => controller.abort()
  }, [loadMajors])

  useEffect(() => {
    const controller = new AbortController()
    void loadOverallScore(controller.signal)
    return () => controller.abort()
  }, [loadOverallScore])

  useEffect(() => {
    const controller = new AbortController()
    void loadGrades(controller.signal)
    return () => controller.abort()
  }, [loadGrades, gradesReloadKey])

  function handleSearch() {
    setStudentName(inputValue.trim())
    writePage(1)
  }

  function handleReset() {
    setInputValue("")
    setStudentName("")
    applyDefaultChain(classes)
  }

  function handleSemesterChange(nextValue: string | null) {
    if (!nextValue) {
      return
    }
    const newest = pickLatest(classesInSemester(classes, nextValue))
    applyClassSelection(newest?.id ?? null, nextValue)
  }

  function handleClassChange(nextValue: string | null) {
    const nextId = nextValue ? Number(nextValue) : NaN
    if (!Number.isFinite(nextId) || nextId < 1) {
      return
    }
    applyClassSelection(nextId)
  }

  function handleExperimentChange(nextValue: string | null) {
    if (!nextValue || nextValue === "none") {
      setExperimentId(null)
      writePage(1)
      return
    }
    const nextId = Number(nextValue)
    if (!Number.isFinite(nextId) || nextId < 1) {
      return
    }
    setExperimentId(nextId)
    writePage(1)
  }

  async function handleExport() {
    if (!canExport || classId == null || experimentId == null) {
      return
    }
    setExporting(true)
    try {
      await downloadGradeExport(classId, experimentId)
    } catch (err) {
      if (err instanceof ApiError) {
        setBanner(err.message || t("analytics:exportFailed"))
      } else {
        setBanner(t("analytics:exportFailed"))
      }
    } finally {
      setExporting(false)
    }
  }

  const hasClasses = classes.length > 0
  const isEmpty = !loading && !error && data?.total === 0
  const hasSearch = studentName.length > 0
  const pageNumbers = data ? Array.from({ length: data.totalPages }, (_, index) => index + 1) : []
  const experimentSelected = data?.experimentSelected ?? experimentId != null

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
              placeholder={t("analytics:searchPlaceholder")}
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
          <Select
            value={semesterKey || undefined}
            onValueChange={handleSemesterChange}
            disabled={!hasClasses}
          >
            <SelectTrigger className="w-[160px]">
              <SelectValue placeholder={t("analytics:semester")} />
            </SelectTrigger>
            <SelectContent>
              {semesters.map((item) => (
                <SelectItem key={item.key} value={item.key}>
                  {item.key === SEMESTER_NONE ? t("analytics:semesterNone") : item.key}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Select
            value={classId != null ? String(classId) : undefined}
            onValueChange={handleClassChange}
            disabled={!hasClasses}
          >
            <SelectTrigger className="w-[200px]">
              <SelectValue placeholder={t("analytics:class")} />
            </SelectTrigger>
            <SelectContent>
              {classesForSemester.map((item) => (
                <SelectItem key={item.id} value={String(item.id)}>
                  {formatClassLabel(item)}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Select
            value={experimentId != null ? String(experimentId) : "none"}
            onValueChange={handleExperimentChange}
            disabled={classId == null}
          >
            <SelectTrigger className="w-[240px]">
              <SelectValue placeholder={t("analytics:assignment")} />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="none">{t("analytics:noAssignment")}</SelectItem>
              {experiments.map((item) => (
                <SelectItem key={item.id} value={String(item.id)}>
                  <span className="flex items-center gap-2">
                    {item.title.trim() ? item.title : t("emptyTitle")}
                    <Badge className={STATUS_BADGE_CLASS[item.status]} variant="outline">
                      {t(`assignments:status.${item.status}`)}
                    </Badge>
                  </span>
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <Button type="button" disabled={!canExport || !pickerReady || exporting} onClick={() => void handleExport()}>
          <Download className="size-4" />
          {t("analytics:exportExcel")}
        </Button>
      </div>

      {canExport ? (
        <p className="text-sm text-muted-foreground">{t("analytics:exportHint")}</p>
      ) : hasClasses ? (
        <p className="text-sm text-muted-foreground">
          {t("analytics:rosterOnly")}
        </p>
      ) : null}

      {!classesLoaded ? (
        <div className="py-16 text-center text-muted-foreground">{t("loading")}</div>
      ) : !hasClasses ? (
        <div className="py-16 text-center text-muted-foreground">
          {t("analytics:noClasses")}
        </div>
      ) : !pickerReady || loading ? (
        <div className="py-16 text-center text-muted-foreground">{t("loading")}</div>
      ) : error ? (
        <div className="flex flex-col items-center justify-center gap-4 py-16 text-center">
          <p className="text-destructive">{error}</p>
          <Button type="button" variant="outline" onClick={() => setGradesReloadKey((key) => key + 1)}>
            {t("retry")}
          </Button>
        </div>
      ) : isEmpty && hasSearch ? (
        <div className="py-16 text-center text-muted-foreground">{t("analytics:noMatch")}</div>
      ) : isEmpty ? (
        <div className="py-16 text-center text-muted-foreground">{t("analytics:emptyClass")}</div>
      ) : (
        <>
          {classesError ? <p className="text-sm text-muted-foreground">{classesError}</p> : null}
          {experimentsError ? (
            <p className="text-sm text-muted-foreground">{experimentsError}</p>
          ) : null}
          {banner ? (
            <div
              role="alert"
              className="flex items-center justify-between gap-4 rounded-md border border-destructive/50 bg-destructive/10 px-4 py-3 text-sm text-destructive"
            >
              <p>{banner}</p>
              <Button
                type="button"
                variant="ghost"
                size="icon-sm"
                aria-label={t("close")}
                className="shrink-0"
                onClick={() => setBanner(null)}
              >
                <X className="size-4" />
              </Button>
            </div>
          ) : null}
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t("analytics:studentId")}</TableHead>
                <TableHead>{t("analytics:name")}</TableHead>
                <TableHead>{t("analytics:major")}</TableHead>
                <TableHead>{t("analytics:score")}</TableHead>
                <TableHead>{t("analytics:total")}</TableHead>
                <TableHead>{t("analytics:status")}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {(data?.items ?? []).map((item) => (
                <TableRow key={item.studentId}>
                  <TableCell>{item.studentId}</TableCell>
                  <TableCell>{item.studentName}</TableCell>
                  <TableCell>{formatMajor(item, majorByStudentId)}</TableCell>
                  <TableCell>
                    {!experimentSelected || item.score == null ? "—" : String(item.score)}
                  </TableCell>
                  <TableCell>
                    {!experimentSelected || overallScore == null ? "—" : String(overallScore)}
                  </TableCell>
                  <TableCell>
                    <GradeStatusCell experimentSelected={experimentSelected} status={item.status} />
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
          {data ? (
            <div className="flex flex-wrap items-center justify-between gap-4">
              <p className="text-sm text-muted-foreground">
                {t("analytics:pageSummary", { total: data.total, page: data.page, totalPages: data.totalPages })}
              </p>
              <div className="flex flex-wrap items-center gap-1">
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  disabled={data.page <= 1}
                  onClick={() => writePage(data.page - 1)}
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
                    onClick={() => writePage(pageNumber)}
                  >
                    {pageNumber}
                  </Button>
                ))}
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  disabled={data.page >= data.totalPages}
                  onClick={() => writePage(data.page + 1)}
                >
                  {t("nextPage")}
                </Button>
              </div>
            </div>
          ) : null}
        </>
      )}
    </div>
  )
}

function formatMajor(
  item: GradeStudentRow,
  majorByStudentId: Map<string, string | null>,
): string {
  const fromRow = item.major?.trim()
  if (fromRow) {
    return fromRow
  }
  const fromRoster = majorByStudentId.get(item.studentId)?.trim()
  return fromRoster ? fromRoster : "—"
}

function GradeStatusCell({
  experimentSelected,
  status,
}: {
  experimentSelected: boolean
  status: string | null
}) {
  const { t } = useTranslation()
  if (!experimentSelected) {
    return "—"
  }
  if (status == null) {
    return <span className="text-muted-foreground">{t("analytics:notSubmitted")}</span>
  }
  if (isGradeRoundStatus(status)) {
    return (
      <Badge className={ROUND_STATUS_BADGE_CLASS[status]} variant="outline">
        {t(`submissions:roundStatus.${status}`)}
      </Badge>
    )
  }
  return (
    <Badge className="border-border bg-muted text-muted-foreground" variant="outline">
      {status}
    </Badge>
  )
}
