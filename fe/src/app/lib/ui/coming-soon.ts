import type { TFunction } from "i18next";
import { toast } from "sonner";

export const comingSoon = (feature: string, t?: TFunction) =>
  toast.info(t ? t("common.comingSoon", { feature }) : `${feature} is coming soon!`);
