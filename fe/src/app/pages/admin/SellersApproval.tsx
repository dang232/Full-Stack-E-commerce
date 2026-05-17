import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { CheckCircle, Search } from "lucide-react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { toast } from "sonner";

import { adminApproveSeller, adminListSellers } from "../../lib/api/endpoints/admin";
import { ApiError } from "../../lib/api/envelope";

export function SellersApproval() {
  const qc = useQueryClient();
  const { t } = useTranslation();
  const [search, setSearch] = useState("");
  const sellersQuery = useQuery({
    queryKey: ["admin", "sellers"],
    queryFn: adminListSellers,
    retry: false,
  });

  const approve = useMutation({
    mutationFn: (id: string) => adminApproveSeller(id),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["admin", "sellers"] });
      toast.success(t("admin.sellers.approveOk"));
    },
    onError: (err) =>
      toast.error(err instanceof ApiError ? err.message : t("admin.sellers.approveErr")),
  });

  const filtered = (sellersQuery.data ?? []).filter((s) =>
    s.shopName.toLowerCase().includes(search.toLowerCase()),
  );

  return (
    <div className="space-y-5">
      <div className="flex items-center justify-between">
        <h2 className="text-xl font-bold text-gray-800">{t("admin.sellers.title")}</h2>
      </div>
      <div className="flex items-center gap-3 bg-white border border-gray-200 rounded-xl px-4 py-2.5 shadow-sm">
        <Search size={16} className="text-gray-400" />
        <input
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder={t("admin.sellers.searchPlaceholder")}
          className="flex-1 text-sm outline-none"
        />
      </div>

      {sellersQuery.isLoading ? (
        <p className="text-sm text-gray-400">{t("admin.sellers.loading")}</p>
      ) : null}
      {sellersQuery.error instanceof ApiError ? (
        <p className="text-sm text-red-500">{sellersQuery.error.message}</p>
      ) : null}
      {!sellersQuery.isLoading && filtered.length === 0 ? (
        <div className="bg-white rounded-2xl p-8 text-center shadow-sm">
          <p className="text-sm text-gray-500">{t("admin.sellers.empty")}</p>
        </div>
      ) : null}

      <div className="bg-white rounded-2xl shadow-sm overflow-hidden">
        <div className="divide-y divide-gray-50">
          {filtered.map((s) => (
            <div key={s.id} className="px-5 py-4 flex items-center justify-between gap-4">
              <div>
                <p className="text-sm font-semibold text-gray-800">{s.shopName}</p>
                <p className="text-xs text-gray-500">
                  {s.appliedAt ?? ""} · {s.status}
                </p>
              </div>
              <button
                onClick={() => approve.mutate(s.id)}
                disabled={approve.isPending}
                className="flex items-center gap-1 px-3 py-1.5 rounded-lg text-xs font-semibold text-white disabled:opacity-50"
                style={{ background: "#00BFB3" }}
              >
                <CheckCircle size={13} /> {t("admin.sellers.approve")}
              </button>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
