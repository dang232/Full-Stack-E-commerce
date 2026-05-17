import { MessageSquare } from "lucide-react";
import { useTranslation } from "react-i18next";

export function SellerReviews() {
  const { t } = useTranslation();

  return (
    <div className="bg-white rounded-2xl p-8 text-center shadow-sm">
      <MessageSquare size={48} className="mx-auto mb-4 text-gray-200" />
      <p className="text-sm text-gray-500">{t("seller.reviewsTab.comingSoon")}</p>
    </div>
  );
}
