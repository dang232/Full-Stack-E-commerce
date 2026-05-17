import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { toast } from "sonner";

import {
  adminCreateCoupon,
  adminDeactivateCoupon,
  adminListCoupons,
  type CouponWriteBody,
} from "../../lib/api/endpoints/admin";
import { ApiError } from "../../lib/api/envelope";
import { formatPrice } from "../../lib/format";

import { CouponDialog } from "./CouponDialog";

export function CouponsManagement() {
  const qc = useQueryClient();
  const { t } = useTranslation();
  const [showCreate, setShowCreate] = useState(false);
  const couponsQuery = useQuery({
    queryKey: ["admin", "coupons"],
    queryFn: adminListCoupons,
    retry: false,
  });

  const createCoupon = useMutation({
    mutationFn: (body: CouponWriteBody) => adminCreateCoupon(body),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["admin", "coupons"] });
      toast.success(t("admin.coupons.createOk"));
      setShowCreate(false);
    },
    onError: (err) =>
      toast.error(err instanceof ApiError ? err.message : t("admin.coupons.createErr")),
  });

  const deactivate = useMutation({
    mutationFn: (id: string) => adminDeactivateCoupon(id),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["admin", "coupons"] });
      toast.success(t("admin.coupons.deactivateOk"));
    },
    onError: (err) =>
      toast.error(err instanceof ApiError ? err.message : t("admin.coupons.deactivateErr")),
  });

  const coupons = couponsQuery.data ?? [];

  return (
    <div className="space-y-5">
      <CouponDialog
        open={showCreate}
        onClose={() => setShowCreate(false)}
        onSubmit={(body) => createCoupon.mutate(body)}
        isSubmitting={createCoupon.isPending}
      />
      <div className="flex items-center justify-between">
        <h2 className="text-xl font-bold text-gray-800">{t("admin.coupons.title")}</h2>
        <button
          onClick={() => setShowCreate(true)}
          disabled={createCoupon.isPending}
          className="px-4 py-2 rounded-xl text-white text-sm font-semibold disabled:opacity-50"
          style={{ background: "#FF6200" }}
        >
          {t("admin.coupons.create")}
        </button>
      </div>

      {couponsQuery.isLoading ? (
        <p className="text-sm text-gray-400">{t("admin.coupons.loading")}</p>
      ) : null}
      {couponsQuery.error instanceof ApiError ? (
        <p className="text-sm text-red-500">{couponsQuery.error.message}</p>
      ) : null}

      <div className="bg-white rounded-2xl shadow-sm overflow-hidden">
        <table className="w-full">
          <thead style={{ background: "#f9fafb" }}>
            <tr>
              {[
                t("admin.coupons.th.code"),
                t("admin.coupons.th.type"),
                t("admin.coupons.th.value"),
                t("admin.coupons.th.status"),
                "",
              ].map((h, i) => (
                <th
                  // eslint-disable-next-line react/no-array-index-key -- table headers are positional, no stable id
                  key={i}
                  className="px-4 py-3 text-xs font-semibold text-gray-500 text-left"
                >
                  {h}
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-50">
            {coupons.map((c) => (
              <tr key={c.id} className="hover:bg-gray-50">
                <td className="px-4 py-3 text-sm font-mono font-bold text-gray-800">{c.code}</td>
                <td className="px-4 py-3 text-sm text-gray-600">{c.type}</td>
                <td className="px-4 py-3 text-sm text-gray-600">
                  {c.type === "PERCENT" ? `${c.value}%` : formatPrice(c.value)}
                </td>
                <td className="px-4 py-3">
                  <span
                    className="px-2.5 py-1 rounded-full text-xs font-semibold"
                    style={{
                      background: c.active ? "#ECFDF5" : "#FEF2F2",
                      color: c.active ? "#10B981" : "#EF4444",
                    }}
                  >
                    {c.active ? t("admin.coupons.active") : t("admin.coupons.inactive")}
                  </span>
                </td>
                <td className="px-4 py-3">
                  {c.active ? (
                    <button
                      onClick={() => deactivate.mutate(c.id)}
                      disabled={deactivate.isPending}
                      className="text-xs font-semibold text-red-500 disabled:opacity-50"
                    >
                      {t("admin.coupons.deactivate")}
                    </button>
                  ) : null}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {!couponsQuery.isLoading && coupons.length === 0 ? (
          <p className="px-5 py-8 text-sm text-gray-400 text-center">{t("admin.coupons.empty")}</p>
        ) : null}
      </div>
    </div>
  );
}
