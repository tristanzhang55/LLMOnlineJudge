import type { resources } from "@/i18n/resources"

declare module "i18next" {
  interface CustomTypeOptions {
    defaultNS: [
      "common",
      "nav",
      "auth",
      "questions",
      "assignments",
      "submissions",
      "disputes",
      "roster",
      "analytics",
      "settings",
      "student",
      "errors",
    ]
    resources: (typeof resources)["zh-CN"]
    returnNull: false
  }
}
