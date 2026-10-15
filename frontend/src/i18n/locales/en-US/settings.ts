import type { DeepStringShape } from "@/i18n/shape"
import type { settings as Zh } from "@/i18n/locales/zh-CN/settings"

export const settings = {
  password: {
    title: "Change password",
    descriptionTeacher: "You will need to sign in again after a successful change.",
    descriptionStudent:
      "You will need to sign in again after a successful change. Forgot your current password? Ask your teacher to reset it.",
    current: "Current password",
    new: "New password",
    confirm: "Confirm new password",
    hint: "New password must be 8-72 characters, different from the current password, and not a default password.",
    show: "Show password",
    hide: "Hide password",
    submit: "Save new password",
    fallback: "Could not change password. Try again later.",
    errors: {
      required: "Current password is required",
      length: "New password must be 8-72 characters",
      mismatch: "The new passwords do not match",
      sameAsOld: "New password cannot match the current password",
      defaultPassword: "New password cannot be a default password",
    },
  },
  profile: {
    title: "Profile",
    descriptionStudent: "If this information is wrong, contact your teacher",
    fallback: "Could not load profile. Confirm the backend is running, then retry.",
    account: "Account",
    studentId: "Student ID",
    name: "Name",
    major: "Major",
    noClass: "Not enrolled in a class",
    class: "Class",
    semester: "Semester",
    joinTime: "Joined",
  },
} as const satisfies DeepStringShape<typeof Zh>
