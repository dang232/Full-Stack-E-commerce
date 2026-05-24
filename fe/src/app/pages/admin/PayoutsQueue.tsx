import { IconArrowsSort, IconSearch } from "@tabler/icons-react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { toast } from "sonner";

import { FormDialog } from "../../components/form-dialog";
import { ApiError } from "../../lib/api";
import {
  adminCompletePayout,
  adminFailPayout,
  adminPendingPayouts,
} from "../../lib/api/endpoints/admin";
import { formatDate, formatPrice } from "../../lib/format";
import { groupByDate } from "../../lib/group-by-date";

export function PayoutsQueue() {
  const qc = useQueryClient();
  const { t, i18n } = useTranslation();
  const [failFor, setFailFor] = useState<string | null>(null);
  const [completeFor, setCompleteFor] = useState<string | null>(null);
  const [search, setSearch] = useState("");
  const [sortBy, setSortBy] = useState<"date" | "amount">("date");
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
      setCompleteFor(null);
    },
    onError: (err) => {
      toast.error(err instanceof ApiError ? err.message : t("admin.payouts.updateErr"));
      // Close the dialog on error too. The BE may have actually succeeded
      // (the response Zod parse can fail even on a 200) — leaving the
      // modal open after a failed-on-FE-but-succeeded-on-BE round-trip
      // strands an admin under an opaque overlay (pt33 race). Re-fetch
      // payouts so the row state reflects whatever the BE thinks.
      void qc.invalidateQueries({ queryKey: ["admin", "payouts"] });
      setCompleteFor(null);
    },
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
  const completeTarget = completeFor ? payouts.find((p) => p.id === completeFor) ?? null : null;

  const filtered = useMemo(() => {
    const term = search.trim().toLowerCase();
    if (term.length === 0) return payouts;
    return payouts.filter(
      (p) =>
        p.sellerId.toLowerCase().includes(term) ||
        p.id.toLowerCase().includes(term),
    );
  }, [payouts, search]);

  const sorted = useMemo(() => {
    if (sortBy === "amount") {
      return [...filtered].sort((a, b) => b.amount - a.amount);
    }
    return [...filtered].sort((a, b) => {
      const ta = a.requestedAt ? Date.parse(a.requestedAt) : 0;
      const tb = b.requestedAt ? Date.parse(b.requestedAt) : 0;
      return tb - ta;
    });
  }, [filtered, sortBy]);

  // Date sections only when sorted by date — under amount sort the
  // grouping would split the comparison the user is trying to make.
  const sections = useMemo(
    () => (sortBy === "date" ? groupByDate(sorted, (p) => p.requestedAt, i18n.language) : null),
    [sorted, sortBy, i18n.language],
  );

  return (
    <div className="space-y-5">
      <FormDialog
        open={!!completeTarget}
        title={t("admin.payouts.completeDialog.title")}
        description={
          completeTarget
            ? t("admin.payouts.completeDialog.subtitle", {
                amount: formatPrice(completeTarget.amount),
                sellerId: completeTarget.sellerId.slice(0, 8),
              })
            : undefined
        }
        submitLabel={t("admin.payouts.completeDialog.submit")}
        submitColor="#10B981"
        fields={[]}
        onClose={() => setCompleteFor(null)}
        onSubmit={() => {
          if (completeTarget) complete.mutate(completeTarget.id);
        }}
        isSubmitting={complete.isPending}
      />
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
      <h2 className="text-xl font-bold text-foreground">{t("admin.payouts.title")}</h2>

      <div className="flex flex-col sm:flex-row sm:items-center gap-3">
        <div className="flex items-center gap-2 bg-card border border-border rounded-xl px-3 py-1.5 shadow-sm flex-1">
          <IconSearch size={14} className="text-muted-foreground" />
          <input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder={t("admin.payouts.searchPlaceholder")}
            className="flex-1 text-sm outline-none bg-transparent"
          />
        </div>
        <button
          onClick={() => setSortBy((s) => (s === "date" ? "amount" : "date"))}
          className="shrink-0 flex items-center gap-1.5 px-3 py-2 rounded-xl border border-border bg-card text-xs font-semibold text-muted-foreground hover:bg-muted"
        >
          <IconArrowsSort size={13} />
          {sortBy === "date" ? t("admin.payouts.sortByAmount") : t("admin.payouts.sortByDate")}
        </button>
      </div>

      {payoutsQuery.isLoading ? (
        <p className="text-sm text-muted-foreground">{t("admin.payouts.loading")}</p>
      ) : null}
      {!payoutsQuery.isLoading && payouts.length === 0 ? (
        <div className="bg-card rounded-2xl p-8 text-center shadow-sm">
          <p className="text-sm text-muted-foreground">{t("admin.payouts.empty")}</p>
        </div>
      ) : null}
      {!payoutsQuery.isLoading && payouts.length > 0 && filtered.length === 0 ? (
        <div className="bg-card rounded-2xl p-6 text-center shadow-sm">
          <p className="text-sm text-muted-foreground">{t("admin.payouts.searchEmpty")}</p>
        </div>
      ) : null}

      {filtered.length > 0 ? (
        <div className="bg-card rounded-2xl shadow-sm overflow-hidden">
          <div className="divide-y divide-gray-50">
            {sections
              ? sections.map((section) => (
                  <div key={section.key}>
                    <div className="px-5 py-2 text-[11px] font-bold uppercase tracking-wide text-muted-foreground bg-muted/40">
                      {t(section.labelKey, section.labelArgs)}
                    </div>
                    {section.items.map((p) => (
                      <PayoutRow
                        key={p.id}
                        p={p}
                        onComplete={() => setCompleteFor(p.id)}
                        onFail={() => setFailFor(p.id)}
                        completePending={complete.isPending}
                        failPending={fail.isPending}
                      />
                    ))}
                  </div>
                ))
              : sorted.map((p) => (
                  <PayoutRow
                    key={p.id}
                    p={p}
                    onComplete={() => setCompleteFor(p.id)}
                    onFail={() => setFailFor(p.id)}
                    completePending={complete.isPending}
                    failPending={fail.isPending}
                  />
                ))}
          </div>
        </div>
      ) : null}
    </div>
  );
}

function PayoutRow({
  p,
  onComplete,
  onFail,
  completePending,
  failPending,
}: {
  p: { id: string; sellerId: string; amount: number; requestedAt?: string };
  onComplete: () => void;
  onFail: () => void;
  completePending: boolean;
  failPending: boolean;
}) {
  const { t } = useTranslation();
  return (
    <div className="px-5 py-4 flex items-center justify-between gap-4">
      <div>
        <p className="text-xs font-mono text-muted-foreground">{p.id}</p>
        <p className="text-sm font-semibold text-foreground">
          {t("admin.payouts.sellerLabel", { id: p.sellerId })}
        </p>
        <p className="text-xs text-muted-foreground">
          {p.requestedAt ? formatDate(p.requestedAt) : ""}
        </p>
      </div>
      <div className="flex items-center gap-3 shrink-0">
        <span className="font-bold text-base" style={{ color: "#FF6200" }}>
          {formatPrice(p.amount)}
        </span>
        <button
          onClick={onComplete}
          disabled={completePending}
          className="px-3 py-1.5 rounded-lg text-xs font-semibold text-white disabled:opacity-50"
          style={{ background: "#10B981" }}
        >
          {t("admin.payouts.complete")}
        </button>
        <button
          onClick={onFail}
          disabled={failPending}
          className="px-3 py-1.5 rounded-lg text-xs font-semibold border border-red-200 text-red-500 disabled:opacity-50"
        >
          {t("admin.payouts.fail")}
        </button>
      </div>
    </div>
  );
}
