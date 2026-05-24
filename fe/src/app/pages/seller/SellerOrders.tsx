import { IconCircleCheck, IconPackage, IconSearch, IconTruck } from "@tabler/icons-react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { toast } from "sonner";

import { FormDialog } from "../../components/form-dialog";
import { StatusPill } from "../../components/status-pill";
import { ApiError } from "../../lib/api";
import {
  sellerAcceptOrder,
  sellerRejectOrder,
  sellerShipOrder,
  type PendingSubOrder,
} from "../../lib/api/endpoints/orders";

import { ShipDialog } from "./ShipDialog";

const STATUS_TABS = [
  { id: "all", match: () => true },
  { id: "pending", match: (s: string) => s.toUpperCase().includes("PENDING") || s.toUpperCase().includes("ACCEPT") && !s.toUpperCase().includes("ACCEPTED") },
  { id: "accepted", match: (s: string) => s.toUpperCase().includes("ACCEPTED") },
  { id: "packed", match: (s: string) => s.toUpperCase().includes("PACKED") || s.toUpperCase().includes("PACK") && !s.toUpperCase().includes("ACCEPTED") },
  { id: "shipped", match: (s: string) => s.toUpperCase().includes("SHIPPED") },
  { id: "cancelled", match: (s: string) => s.toUpperCase().includes("CANCEL") || s.toUpperCase().includes("REJECT") },
] as const;

type TabId = (typeof STATUS_TABS)[number]["id"];

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
  const [tab, setTab] = useState<TabId>("all");
  const [search, setSearch] = useState("");

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

  const filtered = useMemo(() => {
    const tabMatcher = STATUS_TABS.find((s) => s.id === tab)?.match ?? (() => true);
    const term = search.trim().toLowerCase();
    return orders.filter((o) => {
      if (!tabMatcher(o.status)) return false;
      if (term.length === 0) return true;
      return (
        o.id.toLowerCase().includes(term) ||
        String(o.orderId).toLowerCase().includes(term)
      );
    });
  }, [orders, tab, search]);

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
      <h2 className="text-xl font-bold text-foreground">{t("seller.orders.title")}</h2>

      <div className="flex flex-col sm:flex-row sm:items-center gap-3">
        <div className="flex gap-1.5 overflow-x-auto pb-1 scrollbar-hide">
          {STATUS_TABS.map(({ id }) => (
            <button
              key={id}
              onClick={() => setTab(id)}
              className="shrink-0 px-3 py-1.5 rounded-full text-xs font-semibold transition-colors"
              style={{
                background: tab === id ? "rgba(0,191,179,0.12)" : "transparent",
                color: tab === id ? "#00BFB3" : "#6b7280",
                border: tab === id ? "1px solid #00BFB3" : "1px solid #e5e7eb",
              }}
            >
              {t(`seller.orders.tabs.${id}`)}
            </button>
          ))}
        </div>
        <div className="sm:ml-auto flex items-center gap-2 bg-card border border-border rounded-xl px-3 py-1.5 shadow-sm w-full sm:w-72">
          <IconSearch size={14} className="text-muted-foreground" />
          <input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder={t("seller.orders.searchPlaceholder")}
            className="flex-1 text-sm outline-none bg-transparent"
          />
        </div>
      </div>

      {isLoading ? <p className="text-sm text-muted-foreground">{t("seller.orders.loading")}</p> : null}
      {error instanceof ApiError ? (
        <p className="text-sm text-red-500">
          {t("seller.orders.loadError", { message: error.message })}
        </p>
      ) : null}
      {!isLoading && orders.length === 0 && !error ? (
        <div className="bg-card rounded-2xl p-8 text-center shadow-sm">
          <IconPackage size={40} className="mx-auto mb-3 text-gray-300" />
          <p className="text-sm text-muted-foreground">{t("seller.orders.empty")}</p>
        </div>
      ) : null}

      {!isLoading && orders.length > 0 && filtered.length === 0 ? (
        <div className="bg-card rounded-2xl p-6 text-center shadow-sm">
          <p className="text-sm text-muted-foreground">{t("seller.orders.filterEmpty")}</p>
        </div>
      ) : null}

      {filtered.length > 0 ? (
        <div className="divide-y divide-gray-50">
          {filtered.map((order) => {
            const status = order.status.toUpperCase();
            const isPending = status.includes("PENDING") || status.includes("ACCEPT");
            const isAccepted = status.includes("ACCEPTED") || status.includes("PACK");
            return (
              <div key={order.id} className="p-5 flex items-center justify-between gap-4">
                <div>
                  <div className="flex items-center gap-2 mb-1">
                    <span className="text-xs font-mono font-bold text-muted-foreground">{order.id}</span>
                    <StatusPill status={order.status} />
                  </div>
                  <p className="text-sm text-muted-foreground mt-0.5">
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
                        <IconCircleCheck size={13} /> {t("seller.orders.accept")}
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
                      <IconTruck size={13} /> {t("seller.orders.ship")}
                    </button>
                  ) : null}
                </div>
              </div>
            );
          })}
        </div>
      </div>
      ) : null}
    </div>
  );
}
