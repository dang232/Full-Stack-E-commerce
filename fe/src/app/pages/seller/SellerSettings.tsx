import { useTranslation } from "react-i18next";

import { ApiError } from "../../lib/api";

export function SellerSettings({
  profileData,
  profileError,
}: {
  profileData: unknown;
  profileError: unknown;
}) {
  const { t } = useTranslation();

  return (
    <div className="bg-white rounded-2xl p-6 shadow-sm">
      <h2 className="text-xl font-bold text-gray-800 mb-3">{t("seller.settings.title")}</h2>
      <p className="text-sm text-gray-500 mb-4">{t("seller.settings.comingSoon")}</p>
      <div className="space-y-4 text-sm">
        {profileData ? (
          <pre className="bg-gray-50 rounded-xl p-3 text-[11px] overflow-auto">
            {JSON.stringify(profileData, null, 2)}
          </pre>
        ) : null}
        {profileError instanceof ApiError ? (
          <p className="text-sm text-red-500">{profileError.message}</p>
        ) : null}
      </div>
    </div>
  );
}
