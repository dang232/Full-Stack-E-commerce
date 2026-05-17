import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { toast } from "sonner";

import { FormDialog } from "../../components/form-dialog";
import { ApiError } from "../../lib/api";
import {
  adminCompletePayout,
  adminFailPayout,
  adminPendingPayouts,
} from "../../lib/api/endpoints/admin";
import { formatPrice } from "../../lib/format";

export function PayoutsQueue() {
  const qc = useQueryClient();
  const { t } = useTranslation();
  const [failFor, setFailFor] = useState<string | null>(null);
  const payoutsQuery = useQuery({
    queryKey: ["admin", "payouts", "pending"],
    queryFn: adminPendingPayouts,
    retry: false,
  });

  const complete = useMutation({
    mutationFn: (id: string) => adminCompletePayout(id),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["admin", "payouts"] });
      toast.success(t("admin.payouts.completeOk"));
    },
    onError: (err) =>
      toast.error(err instanceof ApiError ? err.message : t("admin.payouts.updateErr")),
  });

  const fail = useMutation({
    mutationFn: ({ id, reason }: { id: string; reason: string }) => adminFailPayout(id, { reason }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["admin", "payouts"] });
      toast.success(t("admin.payouts.failOk"));
      setFailFor(null);
    },
    onError: (err) =>
      toast.error(err instanceof ApiError ? err.message : t("admin.payouts.updateErr")),
  });

  const payouts = payoutsQuery.data ?? [];

  return (
    <div className="space-y-5">
      <FormDialog
        open={!!failFor}
        title={t("admin.payouts.failDialog.title")}
        description={failFor ? t("admin.payouts.failDialog.subtitle", { id: failFor }) : undefined}
        submitLabel={t("admin.payouts.failDialog.submit")}
        submitColor="#EF4444"
        fields={[
          {
            key: "reason",
            label: t("admin.payouts.failDialog.reasonLabel"),
            placeholder: t("admin.payouts.failDialog.reasonPlaceholder"),
            type: "textarea",
            required: true,
          },
        ]}
        onClose={() => setFailFor(null)}
        onSubmit={({ reason }) => {
          if (failFor) fail.mutate({ id: failFor, reason });
        }}
        isSubmitting={fail.isPending}
      />
      <h2 className="text-xl font-bold text-gray-800">{t("admin.payouts.title")}</h2>

      {payoutsQuery.isLoading ? (
        <p className="text-sm text-gray-400">{t("admin.payouts.loading")}</p>
      ) : null}
      {!payoutsQuery.isLoading && payouts.length === 0 ? (
        <div className="bg-white rounded-2xl p-8 text-center shadow-sm">
          <p className="text-sm text-gray-500">{t("admin.payouts.empty")}</p>
        </div>
      ) : null}

      <div className="bg-white rounded-2xl shadow-sm overflow-hidden">
        <div className="divide-y divide-gray-50">
          {payouts.map((p) => (
            <div key={p.id} className="px-5 py-4 flex items-center justify-between gap-4">
              <div>
                <p className="text-xs font-mono text-gray-400">{p.id}</p>
                <p className="text-sm font-semibold text-gray-800">
                  {t("admin.payouts.sellerLabel", { id: p.sellerId })}
                </p>
                <p className="text-xs text-gray-500">{p.requestedAt ?? ""}</p>
              </div>
              <div className="flex items-center gap-3 shrink-0">
                <span className="font-bold text-base" style={{ color: "#FF6200" }}>
                  {formatPrice(p.amount)}
                </span>
                <button
                  onClick={() => complete.mutate(p.id)}
                  disabled={complete.isPending}
                  className="px-3 py-1.5 rounded-lg text-xs font-semibold text-white disabled:opacity-50"
                  style={{ background: "#10B981" }}
                >
                  {t("admin.payouts.complete")}
                </button>
                <button
                  onClick={() => setFailFor(p.id)}
                  disabled={fail.isPending}
                  className="px-3 py-1.5 rounded-lg text-xs font-semibold border border-red-200 text-red-500 disabled:opacity-50"
                >
                  {t("admin.payouts.fail")}
                </button>
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
