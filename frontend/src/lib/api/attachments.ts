import { apiFetch } from "@/lib/api/client"

export type AttachmentOwnerType =
  | "question"
  | "question_answer"
  | "sub_question"
  | "sub_question_answer"
  | "submission"

export interface UploadedAttachment {
  id: number
  url: string
  storageKey: string
  mimeType: string
  fileSize: number
}

export async function uploadAttachment(
  file: File,
  ownerType: AttachmentOwnerType,
  ownerId?: number,
): Promise<UploadedAttachment> {
  const form = new FormData()
  form.append("file", file)
  form.append("ownerType", ownerType)
  if (ownerId != null) {
    form.append("ownerId", String(ownerId))
  }

  const path =
    ownerId != null ? `/attachments?ownerId=${ownerId}` : "/attachments"

  return apiFetch<UploadedAttachment>(path, {
    method: "POST",
    body: form,
  })
}
