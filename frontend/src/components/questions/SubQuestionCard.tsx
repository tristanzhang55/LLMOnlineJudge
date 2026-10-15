import { useRef } from "react"
import { ChevronDown, GripVertical, Trash2 } from "lucide-react"
import { useTranslation } from "react-i18next"

import { MarkdownPreview } from "@/components/questions/MarkdownPreview"
import { Button } from "@/components/ui/button"
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from "@/components/ui/collapsible"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { Switch } from "@/components/ui/switch"
import { Textarea } from "@/components/ui/textarea"
import { IMAGE_ACCEPT, useMarkdownImageUpload } from "@/hooks/useMarkdownImageUpload"
import type { SandboxLanguageOption } from "@/lib/api/questions"
import {
  ANSWER_FORMATS,
  sandboxLanguageLabel,
  type AnswerFormat,
  type SubQuestionFormValue,
} from "@/lib/mock-questions"
import { cn } from "@/lib/utils"

interface SubQuestionCardProps {
  index: number
  value: SubQuestionFormValue
  onChange: (value: SubQuestionFormValue) => void
  onDelete: () => void
  onDragStart: () => void
  onDragOver: (event: React.DragEvent) => void
  onDrop: () => void
  isDragging?: boolean
  errors?: Partial<Record<keyof SubQuestionFormValue, string>>
  languages: SandboxLanguageOption[]
  languagesLoading?: boolean
}

export function SubQuestionCard({
  index,
  value,
  onChange,
  onDelete,
  onDragStart,
  onDragOver,
  onDrop,
  isDragging = false,
  errors,
  languages,
  languagesLoading = false,
}: SubQuestionCardProps) {
  const { t } = useTranslation()
  const contentRef = useRef<HTMLTextAreaElement>(null)
  const referenceRef = useRef<HTMLTextAreaElement>(null)

  const isImageAnswer = value.answerFormat === "image"
  const isCodeAnswer = value.answerFormat === "code"
  const languageSelectDisabled = languagesLoading || languages.length === 0

  const {
    fileInputRef: contentFileInputRef,
    uploading: uploadingContentImage,
    error: contentImageUploadError,
    insertImage: insertContentImage,
  } = useMarkdownImageUpload({
    ownerType: "sub_question",
    textareaRef: contentRef,
    getValue: () => value.content,
    onChange: (next) => onChange({ ...value, content: next }),
  })

  const {
    fileInputRef: referenceFileInputRef,
    uploading: uploadingReferenceImage,
    error: referenceImageUploadError,
    insertImage: insertReferenceImage,
  } = useMarkdownImageUpload({
    ownerType: "sub_question_answer",
    textareaRef: referenceRef,
    getValue: () => value.referenceAnswer ?? "",
    onChange: (next) => onChange({ ...value, referenceAnswer: next }),
  })

  function updateField<K extends keyof SubQuestionFormValue>(
    field: K,
    fieldValue: SubQuestionFormValue[K]
  ) {
    onChange({ ...value, [field]: fieldValue })
  }

  return (
    <Collapsible
      defaultOpen
      className={cn(
        "rounded-xl border bg-card",
        isDragging && "opacity-60"
      )}
      onDragOver={onDragOver}
      onDrop={(event) => {
        event.preventDefault()
        onDrop()
      }}
    >
      <div className="flex items-center gap-2 border-b px-4 py-3">
        <button
          type="button"
          draggable
          onDragStart={(event) => {
            event.dataTransfer.effectAllowed = "move"
            onDragStart()
          }}
          className="cursor-grab text-muted-foreground hover:text-foreground active:cursor-grabbing"
          aria-label={t("dragSortAria")}
        >
          <GripVertical className="size-4" />
        </button>

        <CollapsibleTrigger className="flex flex-1 items-center justify-between gap-2 text-left">
          <span className="font-medium">{t("questions:form.subQuestion", { index: index + 1 })}</span>
          <ChevronDown className="size-4 text-muted-foreground transition-transform in-data-open:rotate-180" />
        </CollapsibleTrigger>

        <Button
          type="button"
          variant="ghost"
          size="icon-sm"
          aria-label={t("deleteSubQuestionAria")}
          onClick={onDelete}
        >
          <Trash2 className="size-4 text-destructive" />
        </Button>
      </div>

      <CollapsibleContent className="space-y-4 px-4 py-4">
        {errors?.id && (
          <p className="text-sm text-destructive">{errors.id}</p>
        )}
        <div className="space-y-2">
          <div className="flex items-center justify-between gap-2">
            <Label htmlFor={`sub-content-${index}`}>{t("questions:form.content")}</Label>
            <div className="flex flex-col items-end gap-1">
              <div className="flex items-center gap-2">
                <input
                  ref={contentFileInputRef}
                  type="file"
                  accept={IMAGE_ACCEPT}
                  className="hidden"
                  onChange={(event) => {
                    const file = event.target.files?.[0]
                    if (file) {
                      void insertContentImage(file)
                    }
                  }}
                />
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  disabled={uploadingContentImage}
                  onClick={() => contentFileInputRef.current?.click()}
                >
                  {uploadingContentImage ? t("uploading") : t("insertImage")}
                </Button>
              </div>
              {contentImageUploadError && (
                <p className="text-sm text-destructive">{contentImageUploadError}</p>
              )}
            </div>
          </div>
          <Textarea
            ref={contentRef}
            id={`sub-content-${index}`}
            value={value.content}
            onChange={(event) => updateField("content", event.target.value)}
            className="min-h-24 font-mono text-sm"
            aria-invalid={Boolean(errors?.content)}
          />
          {errors?.content && (
            <p className="text-sm text-destructive">{errors.content}</p>
          )}
          <div className="space-y-2">
            <Label>{t("preview")}</Label>
            <MarkdownPreview content={value.content} />
          </div>
        </div>

        <div className={cn("grid gap-4", isCodeAnswer ? "md:grid-cols-3" : "md:grid-cols-2")}>
          <div className="space-y-2">
            <Label>{t("questions:form.answerFormat")}</Label>
            <Select
              value={value.answerFormat}
              onValueChange={(nextValue) => {
                const answerFormat = nextValue as AnswerFormat
                onChange({
                  ...value,
                  answerFormat,
                  language: answerFormat === "code" ? value.language : "",
                })
              }}
            >
              <SelectTrigger className="w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {ANSWER_FORMATS.map((key) => (
                  <SelectItem key={key} value={key}>
                    {t(`questions:answerFormat.${key}`)}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          {isCodeAnswer && (
            <div className="space-y-2">
              <Label>{t("questions:form.language")}</Label>
              <Select
                value={value.language || undefined}
                onValueChange={(nextValue) => updateField("language", nextValue ?? "")}
                disabled={languageSelectDisabled}
              >
                <SelectTrigger
                  className="w-full"
                  aria-invalid={Boolean(errors?.language)}
                >
                  <SelectValue placeholder={languagesLoading ? t("loading") : t("questions:form.languagePlaceholder")} />
                </SelectTrigger>
                <SelectContent>
                  {languages.map((item) => (
                    <SelectItem key={item.code} value={item.code}>
                      {sandboxLanguageLabel(item.code)}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              {errors?.language && (
                <p className="text-sm text-destructive">{errors.language}</p>
              )}
            </div>
          )}

          <div className="space-y-2">
            <Label htmlFor={`sub-score-${index}`}>{t("questions:form.score")}</Label>
            <Input
              id={`sub-score-${index}`}
              type="number"
              min={1}
              value={value.score}
              onChange={(event) => updateField("score", Number(event.target.value))}
              aria-invalid={Boolean(errors?.score)}
            />
            {errors?.score && (
              <p className="text-sm text-destructive">{errors.score}</p>
            )}
          </div>
        </div>

        <div className="space-y-2">
          <div className="flex items-center justify-between gap-2">
            <Label htmlFor={`sub-reference-${index}`}>{t("questions:form.referenceAnswer")}</Label>
            {isImageAnswer && (
              <div className="flex flex-col items-end gap-1">
                <div className="flex items-center gap-2">
                  <input
                    ref={referenceFileInputRef}
                    type="file"
                    accept={IMAGE_ACCEPT}
                    className="hidden"
                    onChange={(event) => {
                      const file = event.target.files?.[0]
                      if (file) {
                        void insertReferenceImage(file)
                      }
                    }}
                  />
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    disabled={uploadingReferenceImage}
                    onClick={() => referenceFileInputRef.current?.click()}
                  >
                    {uploadingReferenceImage ? t("uploading") : t("insertImage")}
                  </Button>
                </div>
                {referenceImageUploadError && (
                  <p className="text-sm text-destructive">{referenceImageUploadError}</p>
                )}
              </div>
            )}
          </div>
          <Textarea
            ref={referenceRef}
            id={`sub-reference-${index}`}
            value={value.referenceAnswer ?? ""}
            onChange={(event) => updateField("referenceAnswer", event.target.value)}
            className={cn("min-h-20", isImageAnswer && "font-mono text-sm")}
          />
          {isImageAnswer && (
            <div className="space-y-2">
              <Label>{t("preview")}</Label>
              <MarkdownPreview content={value.referenceAnswer ?? ""} />
            </div>
          )}
        </div>

        <div className="space-y-2">
          <Label htmlFor={`sub-rubric-${index}`}>{t("questions:form.rubric")}</Label>
          <Textarea
            id={`sub-rubric-${index}`}
            value={value.rubric ?? ""}
            onChange={(event) => updateField("rubric", event.target.value)}
            className="min-h-20"
          />
        </div>

        <div className="flex items-center justify-between rounded-lg border px-4 py-3">
          <div className="space-y-1">
            <Label htmlFor={`sub-sandbox-${index}`}>{t("questions:form.allowSandbox")}</Label>
            <p className="text-sm text-muted-foreground">
              {t("questions:form.allowSandboxHint")}
            </p>
          </div>
          <Switch
            id={`sub-sandbox-${index}`}
            checked={value.allowSandbox}
            onCheckedChange={(checked) => updateField("allowSandbox", checked)}
          />
        </div>
      </CollapsibleContent>
    </Collapsible>
  )
}
