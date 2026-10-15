import { apiFetch } from "@/lib/api/client"

interface ApiClass {
  id: number
  name?: string | null
  semester?: string | null
  createTime?: string | null
}

export interface ClassOption {
  id: number
  name: string
  semester: string | null
  createTime: string | null
}

function asNullableTime(value: string | null | undefined): string | null {
  if (typeof value !== "string") {
    return null
  }
  return value.trim() ? value : null
}

export async function fetchClasses(signal?: AbortSignal): Promise<ClassOption[]> {
  const body = await apiFetch<ApiClass[]>("/classes", { signal })
  if (!Array.isArray(body)) {
    return []
  }
  return body.map((item) => ({
    id: item.id,
    name: item.name ?? "",
    semester: item.semester ?? null,
    createTime: asNullableTime(item.createTime),
  }))
}

export function formatClassLabel(item: ClassOption): string {
  if (item.semester?.trim()) {
    return `${item.name}（${item.semester}）`
  }
  return item.name
}

export function buildClassNameMap(classes: ClassOption[]): Map<number, string> {
  return new Map(classes.map((item) => [item.id, item.name]))
}
