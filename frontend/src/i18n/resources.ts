import { common as commonZh } from "@/i18n/locales/zh-CN/common"
import { nav as navZh } from "@/i18n/locales/zh-CN/nav"
import { auth as authZh } from "@/i18n/locales/zh-CN/auth"
import { questions as questionsZh } from "@/i18n/locales/zh-CN/questions"
import { assignments as assignmentsZh } from "@/i18n/locales/zh-CN/assignments"
import { submissions as submissionsZh } from "@/i18n/locales/zh-CN/submissions"
import { disputes as disputesZh } from "@/i18n/locales/zh-CN/disputes"
import { roster as rosterZh } from "@/i18n/locales/zh-CN/roster"
import { analytics as analyticsZh } from "@/i18n/locales/zh-CN/analytics"
import { settings as settingsZh } from "@/i18n/locales/zh-CN/settings"
import { student as studentZh } from "@/i18n/locales/zh-CN/student"
import { errors as errorsZh } from "@/i18n/locales/zh-CN/errors"

import { common as commonEn } from "@/i18n/locales/en-US/common"
import { nav as navEn } from "@/i18n/locales/en-US/nav"
import { auth as authEn } from "@/i18n/locales/en-US/auth"
import { questions as questionsEn } from "@/i18n/locales/en-US/questions"
import { assignments as assignmentsEn } from "@/i18n/locales/en-US/assignments"
import { submissions as submissionsEn } from "@/i18n/locales/en-US/submissions"
import { disputes as disputesEn } from "@/i18n/locales/en-US/disputes"
import { roster as rosterEn } from "@/i18n/locales/en-US/roster"
import { analytics as analyticsEn } from "@/i18n/locales/en-US/analytics"
import { settings as settingsEn } from "@/i18n/locales/en-US/settings"
import { student as studentEn } from "@/i18n/locales/en-US/student"
import { errors as errorsEn } from "@/i18n/locales/en-US/errors"

export const resources = {
  "zh-CN": {
    common: commonZh,
    nav: navZh,
    auth: authZh,
    questions: questionsZh,
    assignments: assignmentsZh,
    submissions: submissionsZh,
    disputes: disputesZh,
    roster: rosterZh,
    analytics: analyticsZh,
    settings: settingsZh,
    student: studentZh,
    errors: errorsZh,
  },
  "en-US": {
    common: commonEn,
    nav: navEn,
    auth: authEn,
    questions: questionsEn,
    assignments: assignmentsEn,
    submissions: submissionsEn,
    disputes: disputesEn,
    roster: rosterEn,
    analytics: analyticsEn,
    settings: settingsEn,
    student: studentEn,
    errors: errorsEn,
  },
} as const
