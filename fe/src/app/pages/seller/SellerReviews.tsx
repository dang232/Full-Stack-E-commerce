import { IconMessage } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

export function SellerReviews() {
  const { t } = useTranslation();

  return (
    <div className="bg-card rounded-2xl p-8 text-center shadow-sm">
      <IconMessage size={48} className="mx-auto mb-4 text-gray-200" />
      <p className="text-sm text-muted-foreground">{t("seller.reviewsTab.comingSoon")}</p>
    </div>
  );
}
