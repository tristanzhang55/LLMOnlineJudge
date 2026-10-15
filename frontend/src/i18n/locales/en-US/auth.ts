import type { DeepStringShape } from "@/i18n/shape"
import type { auth as Zh } from "@/i18n/locales/zh-CN/auth"

export const auth = {
  signIn: "Sign in",
  signingIn: "Signing in...",
  signOut: "Sign out",
  signingOut: "Signing out...",
  subtitle: "Teachers and students share this sign-in",
  noPortal: "This account has no teaching portal.",
  account: "Account",
  password: "Password",
  forgot: "Forgot your password? Ask your teacher to reset it.",
  emptyCredentials: "Enter your account and password",
  loginFailed: "Sign-in failed. Try again later.",
} as const satisfies DeepStringShape<typeof Zh>
