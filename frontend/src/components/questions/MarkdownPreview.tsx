import ReactMarkdown, { defaultUrlTransform } from "react-markdown"
import { useTranslation } from "react-i18next"

import { cn } from "@/lib/utils"

interface MarkdownPreviewProps {
  content: string
  className?: string
}

function previewUrlTransform(url: string): string {
  if (url.startsWith("/api/files/") || url.startsWith("/mock/")) {
    return url
  }

  return defaultUrlTransform(url)
}

export function MarkdownPreview({ content, className }: MarkdownPreviewProps) {
  const { t } = useTranslation()

  if (!content.trim()) {
    return (
      <div
        className={cn(
          "rounded-lg border border-dashed bg-muted/20 px-4 py-8 text-center text-sm text-muted-foreground",
          className
        )}
      >
        {t("questions:markdown.placeholder")}
      </div>
    )
  }

  return (
    <div
      className={cn(
        "rounded-lg border bg-muted/20 px-4 py-3 text-sm leading-relaxed",
        className
      )}
    >
      <ReactMarkdown
        urlTransform={previewUrlTransform}
        components={{
          h1: ({ children }) => (
            <h1 className="mt-2 mb-3 text-xl font-semibold first:mt-0">{children}</h1>
          ),
          h2: ({ children }) => (
            <h2 className="mt-2 mb-2 text-lg font-semibold first:mt-0">{children}</h2>
          ),
          h3: ({ children }) => (
            <h3 className="mt-2 mb-2 text-base font-semibold first:mt-0">{children}</h3>
          ),
          p: ({ children }) => <p className="mb-3 last:mb-0">{children}</p>,
          ul: ({ children }) => (
            <ul className="mb-3 list-disc space-y-1 pl-5 last:mb-0">{children}</ul>
          ),
          ol: ({ children }) => (
            <ol className="mb-3 list-decimal space-y-1 pl-5 last:mb-0">{children}</ol>
          ),
          li: ({ children }) => <li>{children}</li>,
          img: ({ src, alt }) => (
            <img
              src={src}
              alt={alt ?? ""}
              className="my-3 max-w-full rounded-lg border bg-background"
            />
          ),
          a: ({ href, children }) => (
            <a
              href={href}
              className="text-primary underline-offset-4 hover:underline"
              target="_blank"
              rel="noreferrer"
            >
              {children}
            </a>
          ),
        }}
      >
        {content}
      </ReactMarkdown>
    </div>
  )
}
