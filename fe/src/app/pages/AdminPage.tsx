import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  LayoutDashboard,
  Users,
  Package,
  Star,
  Tag,
  AlertCircle,
  Wallet,
  TrendingUp,
  ArrowUpRight,
  CheckCircle,
  XCircle,
  Search,
  BarChart3,
} from "lucide-react";
import { motion } from "motion/react";
import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import {
  AreaChart,
  Area,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
} from "recharts";
import { toast } from "sonner";

import { FormDialog } from "../components/form-dialog";
import { Modal } from "../components/ui/modal";
import {
  adminApproveReview,
  adminApproveSeller,
  adminCompletePayout,
  adminCreateCoupon,
  adminDeactivateCoupon,
  adminFailPayout,
  adminListCoupons,
  adminListSellers,
  adminOpenDisputes,
  adminPendingPayouts,
  adminPendingReviews,
  adminRejectReview,
  adminResolveDispute,
  dashboardRevenue,
  dashboardSummary,
  dashboardTopProducts,
  dashboardTopSellers,
  type CouponWriteBody,
} from "../lib/api/endpoints/admin";
import { ApiError } from "../lib/api/envelope";
import { formatPrice } from "../lib/format";

type AdminTab = "dashboard" | "sellers" | "reviews" | "coupons" | "disputes" | "payouts";

function AdminKPICard({
  icon: Icon,
  label,
  value,
  change,
  color,
  sub,
}: {
  icon: typeof LayoutDashboard;
  label: string;
  value: string;
  change?: string;
  color: string;
  sub?: string;
}) {
  return (
    <div className="bg-white rounded-2xl p-5 shadow-sm">
      <div className="flex items-center justify-between mb-3">
        <div
          className="w-11 h-11 rounded-xl flex items-center justify-center"
          style={{ background: `${color}15` }}
        >
          <Icon size={22} style={{ color }} />
        </div>
        {change ? (
          <div className="flex items-center gap-1 text-xs font-semibold text-green-500">
            <ArrowUpRight size={14} />
            {change}
          </div>
        ) : null}
      </div>
      <p className="text-2xl font-black text-gray-800">{value}</p>
      <p className="text-sm text-gray-500 mt-0.5">{label}</p>
      {sub ? <p className="text-xs text-gray-400 mt-0.5">{sub}</p> : null}
    </div>
  );
}

function AdminDashboard() {
  const { t } = useTranslation();
  const summaryQuery = useQuery({
    queryKey: ["admin", "dashboard", "summary"],
    queryFn: dashboardSummary,
    retry: false,
  });
  const revenueQuery = useQuery({
    queryKey: ["admin", "dashboard", "revenue"],
    queryFn: () => dashboardRevenue({ granularity: "month" }),
    retry: false,
  });
  const topProductsQuery = useQuery({
    queryKey: ["admin", "dashboard", "top-products"],
    queryFn: () => dashboardTopProducts({ limit: 5 }),
    retry: false,
  });
  const topSellersQuery = useQuery({
    queryKey: ["admin", "dashboard", "top-sellers"],
    queryFn: () => dashboardTopSellers({ limit: 5 }),
    retry: false,
  });

  const totalRevenue = summaryQuery.data?.totalRevenue ?? null;
  const totalUsers = summaryQuery.data?.totalUsers ?? null;
  const totalOrders = summaryQuery.data?.totalOrders ?? null;
  const totalSellers = summaryQuery.data?.totalSellers ?? null;

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-xl font-bold text-gray-800">{t("admin.dashboard.title")}</h2>
          <p className="text-sm text-gray-500">{t("admin.dashboard.subtitle")}</p>
        </div>
        <button className="flex items-center gap-2 px-4 py-2 rounded-xl border border-gray-200 bg-white text-sm text-gray-600">
          <BarChart3 size={15} /> {t("admin.dashboard.exportReport")}
        </button>
      </div>

      {summaryQuery.error instanceof ApiError ? (
        <div className="rounded-xl bg-amber-50 border border-amber-200 p-3 text-xs text-amber-800 flex items-start gap-2">
          <AlertCircle size={14} className="shrink-0 mt-0.5" />
          <p>{t("admin.dashboard.kpiLoadFail", { message: summaryQuery.error.message })}</p>
        </div>
      ) : null}

      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <AdminKPICard
          icon={TrendingUp}
          label={t("admin.dashboard.kpi.totalRevenue")}
          value={totalRevenue !== null ? formatPrice(totalRevenue) : "—"}
          color="#00BFB3"
        />
        <AdminKPICard
          icon={Users}
          label={t("admin.dashboard.kpi.totalUsers")}
          value={totalUsers !== null ? totalUsers.toLocaleString() : "—"}
          color="#3B82F6"
        />
        <AdminKPICard
          icon={Package}
          label={t("admin.dashboard.kpi.totalOrders")}
          value={totalOrders !== null ? totalOrders.toLocaleString() : "—"}
          color="#FF6200"
        />
        <AdminKPICard
          icon={Wallet}
          label={t("admin.dashboard.kpi.totalSellers")}
          value={totalSellers !== null ? totalSellers.toLocaleString() : "—"}
          color="#F59E0B"
        />
      </div>

      <div className="bg-white rounded-2xl p-5 shadow-sm">
        <h3 className="font-bold text-gray-800 mb-4">{t("admin.dashboard.revenueTitle")}</h3>
        {revenueQuery.isLoading ? (
          <p className="text-sm text-gray-400">{t("admin.dashboard.loading")}</p>
        ) : null}
        {revenueQuery.data && revenueQuery.data.length > 0 ? (
          <ResponsiveContainer width="100%" height={260}>
            <AreaChart data={revenueQuery.data}>
              <defs>
                <linearGradient id="revGrad" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%" stopColor="#00BFB3" stopOpacity={0.3} />
                  <stop offset="95%" stopColor="#00BFB3" stopOpacity={0} />
                </linearGradient>
              </defs>
              <CartesianGrid strokeDasharray="3 3" stroke="#f3f4f6" />
              <XAxis
                dataKey="date"
                axisLine={false}
                tickLine={false}
                tick={{ fill: "#9ca3af", fontSize: 11 }}
              />
              <YAxis
                axisLine={false}
                tickLine={false}
                tick={{ fill: "#9ca3af", fontSize: 11 }}
                tickFormatter={(v) => `${(v / 1_000_000).toFixed(0)}tr`}
              />
              <Tooltip
                formatter={(v: number) => formatPrice(v)}
                contentStyle={{
                  borderRadius: "12px",
                  border: "none",
                  boxShadow: "0 4px 24px rgba(0,0,0,0.1)",
                }}
              />
              <Area
                type="monotone"
                dataKey="amount"
                stroke="#00BFB3"
                strokeWidth={2.5}
                fill="url(#revGrad)"
              />
            </AreaChart>
          </ResponsiveContainer>
        ) : (
          !revenueQuery.isLoading && (
            <p className="text-sm text-gray-400 text-center py-12">
              {t("admin.dashboard.revenueEmpty")}
            </p>
          )
        )}
      </div>

      <div className="grid lg:grid-cols-2 gap-6">
        <div className="bg-white rounded-2xl p-5 shadow-sm">
          <h3 className="font-bold text-gray-800 mb-4">{t("admin.dashboard.topProducts")}</h3>
          {topProductsQuery.data && topProductsQuery.data.length > 0 ? (
            <ResponsiveContainer width="100%" height={200}>
              <BarChart data={topProductsQuery.data} layout="vertical">
                <CartesianGrid strokeDasharray="3 3" stroke="#f3f4f6" horizontal={false} />
                <XAxis
                  type="number"
                  axisLine={false}
                  tickLine={false}
                  tick={{ fill: "#9ca3af", fontSize: 10 }}
                  tickFormatter={(v) => `${(v / 1_000_000).toFixed(0)}tr`}
                />
                <YAxis
                  type="category"
                  dataKey="name"
                  axisLine={false}
                  tickLine={false}
                  tick={{ fill: "#6b7280", fontSize: 11 }}
                  width={120}
                />
                <Tooltip formatter={(v: number) => formatPrice(v)} />
                <Bar dataKey="revenue" fill="#FF6200" radius={[0, 6, 6, 0]} />
              </BarChart>
            </ResponsiveContainer>
          ) : (
            <p className="text-sm text-gray-400 text-center py-8">{t("admin.dashboard.noData")}</p>
          )}
        </div>

        <div className="bg-white rounded-2xl p-5 shadow-sm">
          <h3 className="font-bold text-gray-800 mb-4">{t("admin.dashboard.topSellers")}</h3>
          {topSellersQuery.data && topSellersQuery.data.length > 0 ? (
            <div className="space-y-3">
              {topSellersQuery.data.map((s, i) => (
                <div key={s.sellerId} className="flex items-center gap-3">
                  <span
                    className="w-6 text-center text-sm font-black"
                    style={{ color: i < 3 ? "#FF6200" : "#9ca3af" }}
                  >
                    {i + 1}
                  </span>
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-medium text-gray-700 truncate">
                      {s.shopName ?? s.sellerId}
                    </p>
                  </div>
                  <span className="font-bold text-sm shrink-0" style={{ color: "#FF6200" }}>
                    {formatPrice(s.revenue)}
                  </span>
                </div>
              ))}
            </div>
          ) : (
            <p className="text-sm text-gray-400 text-center py-8">{t("admin.dashboard.noData")}</p>
          )}
        </div>
      </div>
    </div>
  );
}

function SellersApproval() {
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

function ReviewsModeration() {
  const qc = useQueryClient();
  const { t } = useTranslation();
  const [rejectFor, setRejectFor] = useState<string | null>(null);
  const reviewsQuery = useQuery({
    queryKey: ["admin", "reviews", "pending"],
    queryFn: adminPendingReviews,
    retry: false,
  });

  const approve = useMutation({
    mutationFn: (id: string) => adminApproveReview(id),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["admin", "reviews"] });
      toast.success(t("admin.reviewsModeration.approveOk"));
    },
    onError: (err) =>
      toast.error(err instanceof ApiError ? err.message : t("admin.reviewsModeration.approveErr")),
  });

  const reject = useMutation({
    mutationFn: ({ id, reason }: { id: string; reason: string }) =>
      adminRejectReview(id, { reason }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["admin", "reviews"] });
      toast.success(t("admin.reviewsModeration.rejectOk"));
      setRejectFor(null);
    },
    onError: (err) =>
      toast.error(err instanceof ApiError ? err.message : t("admin.reviewsModeration.rejectErr")),
  });

  const reviews = reviewsQuery.data ?? [];

  return (
    <div className="space-y-5">
      <FormDialog
        open={!!rejectFor}
        title={t("admin.reviewsModeration.rejectDialog.title")}
        submitLabel={t("admin.reviewsModeration.rejectDialog.submit")}
        submitColor="#EF4444"
        fields={[
          {
            key: "reason",
            label: t("admin.reviewsModeration.rejectDialog.reasonLabel"),
            placeholder: t("admin.reviewsModeration.rejectDialog.reasonPlaceholder"),
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
      <h2 className="text-xl font-bold text-gray-800">{t("admin.reviewsModeration.title")}</h2>

      {reviewsQuery.isLoading ? (
        <p className="text-sm text-gray-400">{t("admin.reviewsModeration.loading")}</p>
      ) : null}
      {reviewsQuery.error instanceof ApiError ? (
        <p className="text-sm text-red-500">{reviewsQuery.error.message}</p>
      ) : null}
      {!reviewsQuery.isLoading && reviews.length === 0 ? (
        <div className="bg-white rounded-2xl p-8 text-center shadow-sm">
          <p className="text-sm text-gray-500">{t("admin.reviewsModeration.empty")}</p>
        </div>
      ) : null}

      <div className="space-y-3">
        {reviews.map((r) => (
          <div key={r.id} className="bg-white rounded-2xl p-5 shadow-sm">
            <div className="flex items-start justify-between gap-3 mb-3">
              <div>
                <p className="text-xs font-mono text-gray-400">
                  {t("admin.reviewsModeration.productPrefix", { id: r.productId })}
                </p>
                <p className="text-sm font-semibold text-gray-800">
                  {r.userName ?? r.userId ?? t("admin.reviewsModeration.anonGuest")}
                </p>
              </div>
              <div className="flex items-center gap-0.5">
                {Array.from({ length: 5 }).map((_, i) => (
                  <Star
                    key={i} // eslint-disable-line react/no-array-index-key -- decorative star rating, no stable id
                    size={14}
                    fill={i < r.rating ? "#F59E0B" : "#e5e7eb"}
                    className={i < r.rating ? "text-amber-400" : "text-gray-200"}
                  />
                ))}
              </div>
            </div>
            {r.comment ? (
              <p className="text-sm text-gray-700 mb-3 bg-gray-50 p-3 rounded-xl">{r.comment}</p>
            ) : null}
            <div className="flex gap-2">
              <button
                onClick={() => approve.mutate(r.id)}
                disabled={approve.isPending}
                className="flex items-center gap-1 px-3 py-1.5 rounded-lg text-xs font-semibold text-white disabled:opacity-50"
                style={{ background: "#10B981" }}
              >
                <CheckCircle size={13} /> {t("admin.reviewsModeration.approve")}
              </button>
              <button
                onClick={() => setRejectFor(r.id)}
                disabled={reject.isPending}
                className="flex items-center gap-1 px-3 py-1.5 rounded-lg text-xs font-semibold border border-red-200 text-red-500 disabled:opacity-50"
              >
                <XCircle size={13} /> {t("admin.reviewsModeration.reject")}
              </button>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

function CouponDialog({
  open,
  onClose,
  onSubmit,
  isSubmitting,
}: {
  open: boolean;
  onClose: () => void;
  onSubmit: (body: {
    code: string;
    type: "PERCENT" | "FIXED";
    value: number;
    minOrderValue?: number;
    maxDiscount?: number;
    active: boolean;
  }) => void;
  isSubmitting: boolean;
}) {
  if (!open) return null;
  return <CouponDialogBody onClose={onClose} onSubmit={onSubmit} isSubmitting={isSubmitting} />;
}

function CouponDialogBody({
  onClose,
  onSubmit,
  isSubmitting,
}: {
  onClose: () => void;
  onSubmit: (body: {
    code: string;
    type: "PERCENT" | "FIXED";
    value: number;
    minOrderValue?: number;
    maxDiscount?: number;
    active: boolean;
  }) => void;
  isSubmitting: boolean;
}) {
  const { t } = useTranslation();
  const [code, setCode] = useState("");
  const [type, setType] = useState<"PERCENT" | "FIXED">("PERCENT");
  const [value, setValue] = useState("");
  const [minOrderValue, setMinOrderValue] = useState("");
  const [maxDiscount, setMaxDiscount] = useState("");

  const handleSubmit = () => {
    const trimmedCode = code.trim().toUpperCase();
    if (!trimmedCode) {
      toast.error(t("admin.coupons.dialog.missingCode"));
      return;
    }
    const v = Number(value.replace(/\D/g, ""));
    if (!v || v <= 0) {
      toast.error(t("admin.coupons.dialog.invalidValue"));
      return;
    }
    if (type === "PERCENT" && v > 100) {
      toast.error(t("admin.coupons.dialog.percentTooLarge"));
      return;
    }
    onSubmit({
      code: trimmedCode,
      type,
      value: v,
      minOrderValue: minOrderValue
        ? Number(minOrderValue.replace(/\D/g, "")) || undefined
        : undefined,
      maxDiscount: maxDiscount ? Number(maxDiscount.replace(/\D/g, "")) || undefined : undefined,
      active: true,
    });
  };

  return (
    <Modal
      open
      onClose={onClose}
      dismissDisabled={isSubmitting}
      title={t("admin.coupons.dialog.title")}
      footer={
        <>
          <button
            onClick={onClose}
            disabled={isSubmitting}
            className="flex-1 py-2.5 rounded-xl border border-gray-200 text-sm font-semibold text-gray-600 disabled:opacity-50"
          >
            {t("admin.coupons.dialog.cancel")}
          </button>
          <button
            onClick={handleSubmit}
            disabled={isSubmitting}
            className="flex-1 py-2.5 rounded-xl text-white text-sm font-semibold disabled:opacity-50"
            style={{ background: "#6366F1" }}
          >
            {isSubmitting ? t("admin.coupons.dialog.submitting") : t("admin.coupons.dialog.submit")}
          </button>
        </>
      }
    >
      <div className="space-y-4">
        <div>
          <label
            htmlFor="admin-coupon-code"
            className="block text-sm font-semibold text-gray-700 mb-1.5"
          >
            {t("admin.coupons.dialog.codeLabel")}
          </label>
          <input
            id="admin-coupon-code"
            value={code}
            onChange={(e) => setCode(e.target.value.toUpperCase())}
            placeholder={t("admin.coupons.dialog.codePlaceholder")}
            className="w-full px-3 py-2.5 border border-gray-200 rounded-xl text-sm font-mono uppercase tracking-wider outline-none focus:border-[#6366F1]"
            // eslint-disable-next-line jsx-a11y/no-autofocus -- inside a modal opened by explicit user click; focusing the first input is expected UX
            autoFocus
          />
        </div>

        <div>
          <span className="block text-sm font-semibold text-gray-700 mb-2">
            {t("admin.coupons.dialog.typeLabel")}
          </span>
          <div className="grid grid-cols-2 gap-2">
            {(["PERCENT", "FIXED"] as const).map((opt) => (
              <button
                key={opt}
                type="button"
                onClick={() => setType(opt)}
                className="py-2 rounded-xl text-sm font-medium border transition-colors"
                style={
                  type === opt
                    ? { background: "#6366F1", color: "white", borderColor: "#6366F1" }
                    : { background: "white", color: "#6b7280", borderColor: "#e5e7eb" }
                }
              >
                {opt === "PERCENT"
                  ? t("admin.coupons.dialog.typePercent")
                  : t("admin.coupons.dialog.typeFixed")}
              </button>
            ))}
          </div>
        </div>

        <div>
          <label
            htmlFor="admin-coupon-value"
            className="block text-sm font-semibold text-gray-700 mb-1.5"
          >
            {type === "PERCENT"
              ? t("admin.coupons.dialog.valueLabelPercent")
              : t("admin.coupons.dialog.valueLabelFixed")}
          </label>
          <input
            id="admin-coupon-value"
            value={value}
            onChange={(e) => setValue(e.target.value)}
            placeholder={
              type === "PERCENT"
                ? t("admin.coupons.dialog.valuePlaceholderPercent")
                : t("admin.coupons.dialog.valuePlaceholderFixed")
            }
            className="w-full px-3 py-2.5 border border-gray-200 rounded-xl text-sm outline-none focus:border-[#6366F1]"
          />
        </div>

        <div className="grid grid-cols-2 gap-3">
          <div>
            <label
              htmlFor="admin-coupon-min-order"
              className="block text-sm font-semibold text-gray-700 mb-1.5"
            >
              {t("admin.coupons.dialog.minOrderLabel")}
            </label>
            <input
              id="admin-coupon-min-order"
              value={minOrderValue}
              onChange={(e) => setMinOrderValue(e.target.value)}
              placeholder={t("admin.coupons.dialog.minOrderPlaceholder")}
              className="w-full px-3 py-2.5 border border-gray-200 rounded-xl text-sm outline-none focus:border-[#6366F1]"
            />
          </div>
          {type === "PERCENT" ? (
            <div>
              <label
                htmlFor="admin-coupon-max-discount"
                className="block text-sm font-semibold text-gray-700 mb-1.5"
              >
                {t("admin.coupons.dialog.maxDiscountLabel")}
              </label>
              <input
                id="admin-coupon-max-discount"
                value={maxDiscount}
                onChange={(e) => setMaxDiscount(e.target.value)}
                placeholder={t("admin.coupons.dialog.maxDiscountPlaceholder")}
                className="w-full px-3 py-2.5 border border-gray-200 rounded-xl text-sm outline-none focus:border-[#6366F1]"
              />
            </div>
          ) : null}
        </div>
      </div>
    </Modal>
  );
}

function CouponsManagement() {
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

function DisputesQueue() {
  const qc = useQueryClient();
  const { t } = useTranslation();
  const [resolveFor, setResolveFor] = useState<string | null>(null);
  const disputesQuery = useQuery({
    queryKey: ["admin", "disputes"],
    queryFn: adminOpenDisputes,
    retry: false,
  });

  const resolve = useMutation({
    mutationFn: ({
      id,
      body,
    }: {
      id: string;
      body: { resolution: string; refundAmount?: number };
    }) => adminResolveDispute(id, body),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["admin", "disputes"] });
      toast.success(t("admin.disputes.resolveOk"));
      setResolveFor(null);
    },
    onError: (err) =>
      toast.error(err instanceof ApiError ? err.message : t("admin.disputes.resolveErr")),
  });

  const disputes = disputesQuery.data ?? [];

  return (
    <div className="space-y-5">
      <FormDialog
        open={!!resolveFor}
        title={t("admin.disputes.resolveDialog.title")}
        description={
          resolveFor ? t("admin.disputes.resolveDialog.subtitle", { id: resolveFor }) : undefined
        }
        submitLabel={t("admin.disputes.resolveDialog.submit")}
        submitColor="#00BFB3"
        fields={[
          {
            key: "resolution",
            label: t("admin.disputes.resolveDialog.resolutionLabel"),
            placeholder: t("admin.disputes.resolveDialog.resolutionPlaceholder"),
            type: "textarea",
            required: true,
          },
          {
            key: "refundAmount",
            label: t("admin.disputes.resolveDialog.refundLabel"),
            placeholder: t("admin.disputes.resolveDialog.refundPlaceholder"),
            type: "number",
            required: false,
            helper: t("admin.disputes.resolveDialog.refundHelper"),
          },
        ]}
        onClose={() => setResolveFor(null)}
        onSubmit={({ resolution, refundAmount }) => {
          if (!resolveFor) return;
          const body: { resolution: string; refundAmount?: number } = { resolution };
          if (refundAmount) {
            const parsed = Number(refundAmount.replace(/\D/g, ""));
            if (parsed > 0) body.refundAmount = parsed;
          }
          resolve.mutate({ id: resolveFor, body });
        }}
        isSubmitting={resolve.isPending}
      />
      <h2 className="text-xl font-bold text-gray-800">{t("admin.disputes.title")}</h2>

      {disputesQuery.isLoading ? (
        <p className="text-sm text-gray-400">{t("admin.disputes.loading")}</p>
      ) : null}
      {!disputesQuery.isLoading && disputes.length === 0 ? (
        <div className="bg-white rounded-2xl p-8 text-center shadow-sm">
          <p className="text-sm text-gray-500">{t("admin.disputes.empty")}</p>
        </div>
      ) : null}

      <div className="space-y-3">
        {disputes.map((d) => (
          <div key={d.id} className="bg-white rounded-2xl p-5 shadow-sm">
            <div className="flex items-start justify-between gap-3 mb-3">
              <div>
                <p className="text-xs font-mono text-gray-400">{d.id}</p>
                <p className="text-sm font-semibold text-gray-800">
                  {t("admin.disputes.orderLabel", { id: d.returnId })}
                </p>
                <p className="text-xs text-gray-500">{d.createdAt ?? ""}</p>
              </div>
              <span className="px-2.5 py-1 rounded-full text-xs font-semibold bg-amber-100 text-amber-700">
                {d.status}
              </span>
            </div>
            {d.description ? (
              <p className="text-sm text-gray-700 mb-3 bg-gray-50 p-3 rounded-xl">
                {d.description}
              </p>
            ) : null}
            <button
              onClick={() => setResolveFor(d.id)}
              disabled={resolve.isPending}
              className="px-4 py-2 rounded-lg text-sm font-semibold text-white disabled:opacity-50"
              style={{ background: "#00BFB3" }}
            >
              {t("admin.disputes.resolve")}
            </button>
          </div>
        ))}
      </div>
    </div>
  );
}

function PayoutsQueue() {
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

const NAV_ITEMS: { id: AdminTab; labelKey: string; icon: typeof LayoutDashboard }[] = [
  { id: "dashboard", labelKey: "admin.nav.dashboard", icon: LayoutDashboard },
  { id: "sellers", labelKey: "admin.nav.sellers", icon: Users },
  { id: "reviews", labelKey: "admin.nav.reviews", icon: Star },
  { id: "coupons", labelKey: "admin.nav.coupons", icon: Tag },
  { id: "disputes", labelKey: "admin.nav.disputes", icon: AlertCircle },
  { id: "payouts", labelKey: "admin.nav.payouts", icon: Wallet },
];

export function AdminPage() {
  const [activeTab, setActiveTab] = useState<AdminTab>("dashboard");
  const { t } = useTranslation();

  // Pull pending counts for sidebar badges (best effort).
  const sellersQuery = useQuery({
    queryKey: ["admin", "sellers"],
    queryFn: adminListSellers,
    retry: false,
  });
  const reviewsQuery = useQuery({
    queryKey: ["admin", "reviews", "pending"],
    queryFn: adminPendingReviews,
    retry: false,
  });
  const disputesQuery = useQuery({
    queryKey: ["admin", "disputes"],
    queryFn: adminOpenDisputes,
    retry: false,
  });
  const payoutsQuery = useQuery({
    queryKey: ["admin", "payouts", "pending"],
    queryFn: adminPendingPayouts,
    retry: false,
  });

  const badges = useMemo(
    () => ({
      sellers: sellersQuery.data?.length ?? 0,
      reviews: reviewsQuery.data?.length ?? 0,
      disputes: disputesQuery.data?.length ?? 0,
      payouts: payoutsQuery.data?.length ?? 0,
    }),
    [sellersQuery.data, reviewsQuery.data, disputesQuery.data, payoutsQuery.data],
  );

  return (
    <div className="min-h-screen" style={{ background: "#f4f6f9" }}>
      <div className="max-w-7xl mx-auto px-4 py-6">
        <div className="flex items-center justify-between mb-6 bg-white rounded-2xl p-5 shadow-sm">
          <div className="flex items-center gap-3">
            <div
              className="w-12 h-12 rounded-2xl flex items-center justify-center"
              style={{ background: "linear-gradient(135deg, #6366F1, #8B5CF6)" }}
            >
              <LayoutDashboard size={22} className="text-white" />
            </div>
            <div>
              <h1 className="text-lg font-bold text-gray-800">{t("admin.console")}</h1>
              <p className="text-sm text-gray-500">{t("admin.consoleSub")}</p>
            </div>
          </div>
        </div>

        <div className="flex gap-6">
          <nav className="hidden md:block w-56 shrink-0">
            <div className="bg-white rounded-2xl shadow-sm overflow-hidden">
              {NAV_ITEMS.map((item) => {
                const badge =
                  item.id === "sellers"
                    ? badges.sellers
                    : item.id === "reviews"
                      ? badges.reviews
                      : item.id === "disputes"
                        ? badges.disputes
                        : item.id === "payouts"
                          ? badges.payouts
                          : 0;
                return (
                  <button
                    key={item.id}
                    onClick={() => setActiveTab(item.id)}
                    className="w-full flex items-center gap-3 px-4 py-3.5 text-sm font-medium transition-all text-left border-b border-gray-50 last:border-0"
                    style={{
                      background: activeTab === item.id ? "rgba(99,102,241,0.08)" : "transparent",
                      color: activeTab === item.id ? "#6366F1" : "#6b7280",
                      borderLeft:
                        activeTab === item.id ? "3px solid #6366F1" : "3px solid transparent",
                    }}
                  >
                    <item.icon size={18} />
                    <span className="flex-1">{t(item.labelKey)}</span>
                    {badge > 0 ? (
                      <span
                        className="px-1.5 py-0.5 rounded-full text-[10px] font-bold text-white"
                        style={{ background: "#FF6200" }}
                      >
                        {badge}
                      </span>
                    ) : null}
                  </button>
                );
              })}
            </div>
          </nav>

          <div className="md:hidden flex gap-2 mb-4 overflow-x-auto pb-1 scrollbar-hide w-full">
            {NAV_ITEMS.map((item) => (
              <button
                key={item.id}
                onClick={() => setActiveTab(item.id)}
                className="shrink-0 flex items-center gap-1.5 px-3 py-2 rounded-xl text-xs font-medium transition-all"
                style={
                  activeTab === item.id
                    ? { background: "#6366F1", color: "white" }
                    : { background: "white", color: "#6b7280", border: "1px solid #e5e7eb" }
                }
              >
                <item.icon size={14} /> {t(item.labelKey)}
              </button>
            ))}
          </div>

          <div className="flex-1 min-w-0">
            <motion.div
              key={activeTab}
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.2 }}
            >
              {activeTab === "dashboard" ? <AdminDashboard /> : null}
              {activeTab === "sellers" ? <SellersApproval /> : null}
              {activeTab === "reviews" ? <ReviewsModeration /> : null}
              {activeTab === "coupons" ? <CouponsManagement /> : null}
              {activeTab === "disputes" ? <DisputesQueue /> : null}
              {activeTab === "payouts" ? <PayoutsQueue /> : null}
            </motion.div>
          </div>
        </div>
      </div>
    </div>
  );
}
