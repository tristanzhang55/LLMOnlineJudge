import { useTranslation } from "react-i18next"

import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Button } from "@/components/ui/button"

interface ConfirmSubmitDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  onConfirm: () => void | Promise<void>
  loading?: boolean
}

export function ConfirmSubmitDialog({
  open,
  onOpenChange,
  onConfirm,
  loading = false,
}: ConfirmSubmitDialogProps) {
  const { t } = useTranslation()

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{t("student:confirm.title")}</DialogTitle>
          <DialogDescription>
            {t("student:confirm.body")}
            <br />
            {t("student:confirm.emptyHint")}
          </DialogDescription>
        </DialogHeader>
        <DialogFooter className="border-t-0 bg-transparent p-0 pt-2 sm:justify-end">
          <Button
            type="button"
            variant="outline"
            disabled={loading}
            onClick={() => onOpenChange(false)}
          >
            {t("cancel")}
          </Button>
          <Button type="button" disabled={loading} onClick={() => void onConfirm()}>
            {loading ? t("student:confirm.submitting") : t("student:confirm.submit")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
