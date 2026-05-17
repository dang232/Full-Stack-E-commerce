import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { toast } from "sonner";

import { FormDialog } from "../../components/form-dialog";
import { ApiError } from "../../lib/api";
import { requestPayout, type Payout } from "../../lib/api/endpoints/seller-finance";
import { formatPrice } from "../../lib/format";

export function SellerWallet({
  balance,
  payouts,
  isLoading,
  error,
}: {
  balance: number | null;
  payouts: Payout[];
  isLoading: boolean;
  error: unknown;
}) {
  const qc = useQueryClient();
  const { t } = useTranslation();
  const [showPayoutDialog, setShowPayoutDialog] = useState(false);
  const requestPayoutMutation = useMutation({
    mutationFn: (body: { amount: number; bankAccount: string }) => requestPayout(body),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["seller", "wallet"] });
      void qc.invalidateQueries({ queryKey: ["seller", "payouts"] });
      toast.success(t("seller.wallet.payoutOk"));
      setShowPayoutDialog(false);
    },
    onError: (err) =>
      toast.error(err instanceof ApiError ? err.message : t("seller.wallet.payoutErr")),
  });

  return (
    <div className="space-y-5">
      <FormDialog
        open={showPayoutDialog}
        title={t("seller.wallet.payoutDialog.title")}
        description={
          balance !== null
            ? t("seller.wallet.payoutDialog.balanceHint", { balance: formatPrice(balance) })
            : undefined
        }
        submitLabel={t("seller.wallet.payoutDialog.submit")}
        submitColor="#00BFB3"
        fields={[
          {
            key: "amount",
            label: t("seller.wallet.payoutDialog.amountLabel"),
            placeholder: t("seller.wallet.payoutDialog.amountPlaceholder"),
            type: "number",
            required: true,
          },
          {
            key: "bankAccount",
            label: t("seller.wallet.payoutDialog.bankLabel"),
            placeholder: t("seller.wallet.payoutDialog.bankPlaceholder"),
            required: true,
          },
        ]}
        onClose={() => setShowPayoutDialog(false)}
        onSubmit={({ amount, bankAccount }) => {
          const parsed = Number(amount.replace(/\D/g, ""));
          if (!parsed || parsed <= 0) {
            toast.error(t("seller.wallet.payoutDialog.invalidAmount"));
            return;
          }
          if (balance !== null && parsed > balance) {
            toast.error(t("seller.wallet.payoutDialog.exceedsBalance"));
            return;
          }
          requestPayoutMutation.mutate({ amount: parsed, bankAccount });
        }}
        isSubmitting={requestPayoutMutation.isPending}
      />
      <h2 className="text-xl font-bold text-gray-800">{t("seller.wallet.title")}</h2>

      <div
        className="rounded-2xl p-6 text-white"
        style={{ background: "linear-gradient(135deg, #00BFB3, #006b65)" }}
      >
        <p className="text-white/70 text-sm mb-2">{t("seller.wallet.balanceLabel")}</p>
        <p className="text-4xl font-black mb-4">
          {balance !== null ? formatPrice(balance) : isLoading ? t("seller.wallet.loading") : "—"}
        </p>
        <div className="flex gap-3">
          <button
            onClick={() => setShowPayoutDialog(true)}
            disabled={requestPayoutMutation.isPending || balance === null || balance <= 0}
            className="px-5 py-2.5 rounded-xl bg-white/20 font-semibold text-sm hover:bg-white/30 transition-colors disabled:opacity-50"
          >
            {requestPayoutMutation.isPending
              ? t("seller.wallet.withdrawing")
              : t("seller.wallet.withdraw")}
          </button>
        </div>
      </div>

      {error instanceof ApiError ? (
        <p className="text-sm text-red-500">
          {t("seller.wallet.loadError", { message: error.message })}
        </p>
      ) : null}

      <div className="bg-white rounded-2xl shadow-sm overflow-hidden">
        <h3 className="px-5 py-4 font-bold text-gray-800 border-b border-gray-100">
          {t("seller.wallet.historyTitle")}
        </h3>
        {payouts.length === 0 ? (
          <p className="px-5 py-8 text-sm text-gray-400 text-center">
            {t("seller.wallet.historyEmpty")}
          </p>
        ) : null}
        <div className="divide-y divide-gray-50">
          {payouts.map((p) => (
            <div key={p.id} className="px-5 py-4 flex items-center justify-between">
              <div>
                <p className="text-sm font-semibold text-gray-800">{formatPrice(p.amount)}</p>
                <p className="text-xs text-gray-500">{p.requestedAt ?? "—"}</p>
              </div>
              <span
                className="px-2.5 py-1 rounded-full text-xs font-semibold"
                style={{
                  background: p.status.toUpperCase() === "COMPLETED" ? "#ECFDF5" : "#FEF3C7",
                  color: p.status.toUpperCase() === "COMPLETED" ? "#10B981" : "#F59E0B",
                }}
              >
                {p.status}
              </span>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
