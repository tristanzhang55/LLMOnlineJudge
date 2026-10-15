import type { DeepStringShape } from "@/i18n/shape"
import type { nav as Zh } from "@/i18n/locales/zh-CN/nav"

export const nav = {
  home: "Home",
  questions: "Question bank",
  questionCreate: "Create question",
  questionPreview: "Question preview",
  questionEdit: "Edit question",
  assignments: "Assignments",
  assignmentCreate: "Create assignment",
  assignmentEdit: "Edit assignment",
  assignmentDetail: "Assignment details",
  submissionList: "Submission list",
  submissions: "Submissions",
  submissionDetail: "Submission details",
  disputes: "Disputes",
  myDisputes: "My disputes",
  grades: "Grades",
  roster: "Roster",
  settings: "Settings",
  brandSuffix: "Linux Judge",
} as const satisfies DeepStringShape<typeof Zh>
