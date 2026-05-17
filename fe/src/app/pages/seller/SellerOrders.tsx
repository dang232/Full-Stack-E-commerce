import { useMutation, useQueryClient } from "@tanstack/react-query";
import { CheckCircle, Package, Truck } from "lucide-react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { toast } from "sonner";

import { FormDialog } from "../../components/form-dialog";
import { ApiError } from "../../lib/api";
import {
  sellerAcceptOrder,
  sellerRejectOrder,
  sellerShipOrder,
  type PendingSubOrder,
} from "../../lib/api/endpoints/orders";

import { ShipDialog } from "./ShipDialog";

export function SellerOrders({
  orders,
  isLoading,
  error,
}: {
  orders: PendingSubOrder[];
  isLoading: boolean;
  error: unknown;
}) {
  const qc = useQueryClient();
  const { t } = useTranslation();
  const [shipFor, setShipFor] = useState<string | null>(null);
  const [rejectFor, setRejectFor] = useState<string | null>(null);

  const accept = useMutation({
    mutationFn: (subOrderId: string) => sellerAcceptOrder(subOrderId),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["seller", "pending-orders"] });
      toast.success(t("seller.orders.acceptOk"));
    },
    onError: (err) =>
      toast.error(err instanceof ApiError ? err.message : t("seller.orders.acceptErr")),
  });

  const reject = useMutation({
    mutationFn: ({ id, reason }: { id: string; reason: string }) =>
      sellerRejectOrder(id, { reason }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["seller", "pending-orders"] });
      toast.success(t("seller.orders.rejectOk"));
      setRejectFor(null);
    },
    onError: (err) =>
      toast.error(err instanceof ApiError ? err.message : t("seller.orders.rejectErr")),
  });

  const ship = useMutation({
    mutationFn: ({ id, body }: { id: string; body: { carrier: string; trackingNumber: string } }) =>
      sellerShipOrder(id, body),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["seller", "pending-orders"] });
      toast.success(t("seller.orders.shipOk"));
      setShipFor(null);
    },
    onError: (err) =>
      toast.error(err instanceof ApiError ? err.message : t("seller.orders.shipErr")),
  });

  return (
    <div className="space-y-5">
      <ShipDialog
        subOrderId={shipFor}
        onClose={() => setShipFor(null)}
        onSubmit={(body) => {
          if (shipFor) ship.mutate({ id: shipFor, body });
        }}
        isSubmitting={ship.isPending}
      />
      <FormDialog
        open={!!rejectFor}
        title={t("seller.rejectDialog.title")}
        description={rejectFor ? t("seller.rejectDialog.subtitle", { id: rejectFor }) : undefined}
        submitLabel={t("seller.rejectDialog.submitLabel")}
        submitColor="#EF4444"
        fields={[
          {
            key: "reason",
            label: t("seller.rejectDialog.reasonLabel"),
            placeholder: t("seller.rejectDialog.reasonPlaceholder"),
            type: "textarea",
            required: true,
          },
        ]}
        onClose={() => setRejectFor(null)}
        onSubmit={({ reason }) => {
          if (rejectFor) reject.mutate({ id: rejectFor, reason });
        }}
        isSubmitting={reject.isPending}
      />
      <h2 className="text-xl font-bold text-gray-800">{t("seller.orders.title")}</h2>

      {isLoading ? <p className="text-sm text-gray-400">{t("seller.orders.loading")}</p> : null}
      {error instanceof ApiError ? (
        <p className="text-sm text-red-500">
          {t("seller.orders.loadError", { message: error.message })}
        </p>
      ) : null}
      {!isLoading && orders.length === 0 && !error ? (
        <div className="bg-white rounded-2xl p-8 text-center shadow-sm">
          <Package size={40} className="mx-auto mb-3 text-gray-300" />
          <p className="text-sm text-gray-500">{t("seller.orders.empty")}</p>
        </div>
      ) : null}

      <div className="bg-white rounded-2xl shadow-sm overflow-hidden">
        <div className="divide-y divide-gray-50">
          {orders.map((order) => {
            const status = order.status.toUpperCase();
            const isPending = status.includes("PENDING") || status.includes("ACCEPT");
            const isAccepted = status.includes("ACCEPTED") || status.includes("PACK");
            return (
              <div key={order.id} className="p-5 flex items-center justify-between gap-4">
                <div>
                  <div className="flex items-center gap-2 mb-1">
                    <span className="text-xs font-mono font-bold text-gray-500">{order.id}</span>
                    <span className="px-2 py-0.5 rounded-full text-xs font-semibold bg-yellow-100 text-yellow-600">
                      {order.status}
                    </span>
                  </div>
                  <p className="text-sm text-gray-500 mt-0.5">
                    {t("seller.orders.parentOrder", { id: order.orderId })}
                  </p>
                </div>
                <div className="flex gap-2 shrink-0">
                  {isPending ? (
                    <>
                      <button
                        onClick={() => accept.mutate(order.id)}
                        disabled={accept.isPending}
                        className="flex items-center gap-1 px-3 py-1.5 rounded-lg text-xs font-semibold text-white disabled:opacity-50"
                        style={{ background: "#00BFB3" }}
                      >
                        <CheckCircle size={13} /> {t("seller.orders.accept")}
                      </button>
                      <button
                        onClick={() => setRejectFor(order.id)}
                        disabled={reject.isPending}
                        className="flex items-center gap-1 px-3 py-1.5 rounded-lg text-xs font-semibold border border-red-200 text-red-500 disabled:opacity-50"
                      >
                        {t("seller.orders.reject")}
                      </button>
                    </>
                  ) : null}
                  {isAccepted ? (
                    <button
                      onClick={() => setShipFor(order.id)}
                      disabled={ship.isPending}
                      className="flex items-center gap-1 px-3 py-1.5 rounded-lg text-xs font-semibold text-white disabled:opacity-50"
                      style={{ background: "#FF6200" }}
                    >
                      <Truck size={13} /> {t("seller.orders.ship")}
                    </button>
                  ) : null}
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}
