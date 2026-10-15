import { useRef, useState, type RefObject } from "react"
import { useTranslation } from "react-i18next"

import { uploadAttachment, type AttachmentOwnerType } from "@/lib/api/attachments"
import { ApiError } from "@/lib/api/client"

const MAX_FILE_SIZE = 5 * 1024 * 1024
const ALLOWED_TYPES = ["image/png", "image/jpeg", "image/gif", "image/webp"]

export const IMAGE_ACCEPT = "image/png,image/jpeg,image/gif,image/webp"

export function useMarkdownImageUpload(options: {
  ownerType: AttachmentOwnerType
  textareaRef: RefObject<HTMLTextAreaElement | null>
  getValue: () => string
  onChange: (next: string) => void
}) {
  const { t } = useTranslation()
  const fileInputRef = useRef<HTMLInputElement>(null)
  const [uploading, setUploading] = useState(false)
  const [error, setError] = useState<string>()

  function clearFileInput() {
    if (fileInputRef.current) {
      fileInputRef.current.value = ""
    }
  }

  async function insertImage(file: File) {
    setError(undefined)

    if (file.size > MAX_FILE_SIZE) {
      setError(t("questions:upload.tooLarge"))
      clearFileInput()
      return
    }

    if (!ALLOWED_TYPES.includes(file.type)) {
      setError(t("questions:upload.badType"))
      clearFileInput()
      return
    }

    setUploading(true)
    try {
      const { url } = await uploadAttachment(file, options.ownerType)
      const textarea = options.textareaRef.current
      const currentContent = options.getValue()
      const start = textarea?.selectionStart ?? currentContent.length
      const end = textarea?.selectionEnd ?? currentContent.length
      const snippet = `![](${url})`
      const nextContent =
        currentContent.slice(0, start) + snippet + currentContent.slice(end)
      options.onChange(nextContent)

      requestAnimationFrame(() => {
        if (textarea) {
          textarea.focus()
          const cursor = start + snippet.length
          textarea.setSelectionRange(cursor, cursor)
        }
      })
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.message)
      } else {
        setError(t("questions:upload.failed"))
      }
    } finally {
      setUploading(false)
      clearFileInput()
    }
  }

  function clearError() {
    setError(undefined)
  }

  return { fileInputRef, uploading, error, insertImage, clearError }
}
