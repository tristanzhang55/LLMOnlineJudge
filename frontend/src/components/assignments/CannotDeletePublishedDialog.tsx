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

interface CannotDeletePublishedDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  itemTitle: string
  onGoUnpublish: () => void
}

export function CannotDeletePublishedDialog({
  open,
  onOpenChange,
  itemTitle,
  onGoUnpublish,
}: CannotDeletePublishedDialogProps) {
  const { t } = useTranslation()

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{t("assignments:cannotDelete.title")}</DialogTitle>
          <DialogDescription>
            {t("assignments:cannotDelete.body", { title: itemTitle })}
          </DialogDescription>
        </DialogHeader>
        <DialogFooter className="border-t-0 bg-transparent p-0 pt-2 sm:justify-end">
          <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
            {t("cancel")}
          </Button>
          <Button type="button" onClick={onGoUnpublish}>
            {t("assignments:cannotDelete.goUnpublish")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
