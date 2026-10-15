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

interface ConfirmUnpublishDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  itemTitle: string
  onConfirm: () => void | Promise<void>
  loading?: boolean
}

export function ConfirmUnpublishDialog({
  open,
  onOpenChange,
  itemTitle,
  onConfirm,
  loading = false,
}: ConfirmUnpublishDialogProps) {
  const { t } = useTranslation()

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{t("assignments:unpublish.title")}</DialogTitle>
          <DialogDescription>
            {t("assignments:unpublish.body", { title: itemTitle })}
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
          <Button
            type="button"
            variant="destructive"
            disabled={loading}
            onClick={() => void onConfirm()}
          >
            {loading ? t("assignments:unpublish.unpublishing") : t("assignments:unpublish.confirm")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
