import fs from "node:fs"
import path from "node:path"

const ROOT = path.resolve("src")
const CJK = /[\u4e00-\u9fff]/
const hits = []

function excluded(file) {
  const normalized = file.split(path.sep).join("/")
  if (normalized.includes("/components/ui/")) {
    return true
  }
  if (/\.test\.(ts|tsx)$/.test(normalized)) {
    return true
  }
  if (/\/lib\/mock-[^/]+\.ts$/.test(normalized)) {
    return true
  }
  if (normalized.includes("/i18n/locales/zh-CN/")) {
    return true
  }
  return false
}

function isCommentOnly(line) {
  const trimmed = line.trim()
  return trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")
}

function walk(dir) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name)
    if (entry.isDirectory()) {
      walk(full)
      continue
    }
    if (!/\.(ts|tsx)$/.test(entry.name) || excluded(full)) {
      continue
    }
    const lines = fs.readFileSync(full, "utf8").split(/\n/)
    lines.forEach((line, index) => {
      if (isCommentOnly(line) || !CJK.test(line)) {
        return
      }
      hits.push(`${path.relative(process.cwd(), full).split(path.sep).join("/")}:${index + 1}`)
    })
  }
}

walk(ROOT)

if (hits.length > 0) {
  for (const hit of hits) {
    console.error(hit)
  }
  process.exit(1)
}

console.log("i18n:check ok")
