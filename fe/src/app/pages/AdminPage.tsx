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
          <h2 className="text-xl font-bold text-gray-800">Admin Dashboard</h2>
          <p className="text-sm text-gray-500">Dữ liệu thời gian thực từ máy chủ</p>
        </div>
        <button className="flex items-center gap-2 px-4 py-2 rounded-xl border border-gray-200 bg-white text-sm text-gray-600">
          <BarChart3 size={15} /> Xuất báo cáo
        </button>
      </div>

      {summaryQuery.error instanceof ApiError ? (
        <div className="rounded-xl bg-amber-50 border border-amber-200 p-3 text-xs text-amber-800 flex items-start gap-2">
          <AlertCircle size={14} className="shrink-0 mt-0.5" />
          <p>Không tải được KPI: {summaryQuery.error.message}</p>
        </div>
      ) : null}

      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <AdminKPICard
          icon={TrendingUp}
          label="Tổng doanh thu"
          value={totalRevenue !== null ? formatPrice(totalRevenue) : "—"}
          color="#00BFB3"
        />
        <AdminKPICard
          icon={Users}
          label="Người dùng"
          value={totalUsers !== null ? totalUsers.toLocaleString() : "—"}
          color="#3B82F6"
        />
        <AdminKPICard
          icon={Package}
          label="Đơn hàng"
          value={totalOrders !== null ? totalOrders.toLocaleString() : "—"}
          color="#FF6200"
        />
        <AdminKPICard
          icon={Wallet}
          label="Người bán"
          value={totalSellers !== null ? totalSellers.toLocaleString() : "—"}
          color="#F59E0B"
        />
      </div>

      <div className="bg-white rounded-2xl p-5 shadow-sm">
        <h3 className="font-bold text-gray-800 mb-4">Doanh thu theo thời gian</h3>
        {revenueQuery.isLoading ? <p className="text-sm text-gray-400">Đang tải...</p> : null}
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
            <p className="text-sm text-gray-400 text-center py-12">Chưa có dữ liệu doanh thu</p>
          )
        )}
      </div>

      <div className="grid lg:grid-cols-2 gap-6">
        <div className="bg-white rounded-2xl p-5 shadow-sm">
          <h3 className="font-bold text-gray-800 mb-4">Sản phẩm bán chạy</h3>
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
            <p className="text-sm text-gray-400 text-center py-8">Chưa có dữ liệu</p>
          )}
        </div>

        <div className="bg-white rounded-2xl p-5 shadow-sm">
          <h3 className="font-bold text-gray-800 mb-4">Người bán dẫn đầu</h3>
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
            <p className="text-sm text-gray-400 text-center py-8">Chưa có dữ liệu</p>
          )}
        </div>
      </div>
    </div>
  );
}

function SellersApproval() {
  const qc = useQueryClient();
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
      toast.success("Đã duyệt seller");
    },
    onError: (err) => toast.error(err instanceof ApiError ? err.message : "Không thể duyệt seller"),
  });

  const filtered = (sellersQuery.data ?? []).filter((s) =>
    s.shopName.toLowerCase().includes(search.toLowerCase()),
  );

  return (
    <div className="space-y-5">
      <div className="flex items-center justify-between">
        <h2 className="text-xl font-bold text-gray-800">Duyệt Seller</h2>
      </div>
      <div className="flex items-center gap-3 bg-white border border-gray-200 rounded-xl px-4 py-2.5 shadow-sm">
        <Search size={16} className="text-gray-400" />
        <input
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Tìm shop..."
          className="flex-1 text-sm outline-none"
        />
      </div>

      {sellersQuery.isLoading ? <p className="text-sm text-gray-400">Đang tải...</p> : null}
      {sellersQuery.error instanceof ApiError ? (
        <p className="text-sm text-red-500">{sellersQuery.error.message}</p>
      ) : null}
      {!sellersQuery.isLoading && filtered.length === 0 ? (
        <div className="bg-white rounded-2xl p-8 text-center shadow-sm">
          <p className="text-sm text-gray-500">Không có seller nào chờ duyệt</p>
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
                <CheckCircle size={13} /> Duyệt
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
      toast.success("Đã duyệt đánh giá");
    },
    onError: (err) => toast.error(err instanceof ApiError ? err.message : "Không thể duyệt"),
  });

  const reject = useMutation({
    mutationFn: ({ id, reason }: { id: string; reason: string }) =>
      adminRejectReview(id, { reason }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["admin", "reviews"] });
      toast.success("Đã từ chối đánh giá");
      setRejectFor(null);
    },
    onError: (err) => toast.error(err instanceof ApiError ? err.message : "Không thể từ chối"),
  });

  const reviews = reviewsQuery.data ?? [];

  return (
    <div className="space-y-5">
      <FormDialog
        open={!!rejectFor}
        title="Từ chối đánh giá"
        submitLabel="Từ chối"
        submitColor="#EF4444"
        fields={[
          {
            key: "reason",
            label: "Lý do từ chối",
            placeholder: "Nội dung không phù hợp, spam, ...",
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
      <h2 className="text-xl font-bold text-gray-800">Kiểm duyệt đánh giá</h2>

      {reviewsQuery.isLoading ? <p className="text-sm text-gray-400">Đang tải...</p> : null}
      {reviewsQuery.error instanceof ApiError ? (
        <p className="text-sm text-red-500">{reviewsQuery.error.message}</p>
      ) : null}
      {!reviewsQuery.isLoading && reviews.length === 0 ? (
        <div className="bg-white rounded-2xl p-8 text-center shadow-sm">
          <p className="text-sm text-gray-500">Không có đánh giá nào cần duyệt</p>
        </div>
      ) : null}

      <div className="space-y-3">
        {reviews.map((r) => (
          <div key={r.id} className="bg-white rounded-2xl p-5 shadow-sm">
            <div className="flex items-start justify-between gap-3 mb-3">
              <div>
                <p className="text-xs font-mono text-gray-400">SP: {r.productId}</p>
                <p className="text-sm font-semibold text-gray-800">
                  {r.userName ?? r.userId ?? "Người dùng"}
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
                <CheckCircle size={13} /> Duyệt
              </button>
              <button
                onClick={() => setRejectFor(r.id)}
                disabled={reject.isPending}
                className="flex items-center gap-1 px-3 py-1.5 rounded-lg text-xs font-semibold border border-red-200 text-red-500 disabled:opacity-50"
              >
                <XCircle size={13} /> Từ chối
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
  const [code, setCode] = useState("");
  const [type, setType] = useState<"PERCENT" | "FIXED">("PERCENT");
  const [value, setValue] = useState("");
  const [minOrderValue, setMinOrderValue] = useState("");
  const [maxDiscount, setMaxDiscount] = useState("");

  const handleSubmit = () => {
    const trimmedCode = code.trim().toUpperCase();
    if (!trimmedCode) {
      toast.error("Vui lòng nhập mã coupon");
      return;
    }
    const v = Number(value.replace(/\D/g, ""));
    if (!v || v <= 0) {
      toast.error("Giá trị không hợp lệ");
      return;
    }
    if (type === "PERCENT" && v > 100) {
      toast.error("Giảm phần trăm tối đa 100");
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
      title="Tạo coupon mới"
      footer={
        <>
          <button
            onClick={onClose}
            disabled={isSubmitting}
            className="flex-1 py-2.5 rounded-xl border border-gray-200 text-sm font-semibold text-gray-600 disabled:opacity-50"
          >
            Huỷ
          </button>
          <button
            onClick={handleSubmit}
            disabled={isSubmitting}
            className="flex-1 py-2.5 rounded-xl text-white text-sm font-semibold disabled:opacity-50"
            style={{ background: "#6366F1" }}
          >
            {isSubmitting ? "Đang tạo..." : "Tạo coupon"}
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
            Mã coupon
          </label>
          <input
            id="admin-coupon-code"
            value={code}
            onChange={(e) => setCode(e.target.value.toUpperCase())}
            placeholder="VD: SALE50"
            className="w-full px-3 py-2.5 border border-gray-200 rounded-xl text-sm font-mono uppercase tracking-wider outline-none focus:border-[#6366F1]"
            autoFocus
          />
        </div>

        <div>
          <span className="block text-sm font-semibold text-gray-700 mb-2">Loại giảm giá</span>
          <div className="grid grid-cols-2 gap-2">
            {(["PERCENT", "FIXED"] as const).map((t) => (
              <button
                key={t}
                type="button"
                onClick={() => setType(t)}
                className="py-2 rounded-xl text-sm font-medium border transition-colors"
                style={
                  type === t
                    ? { background: "#6366F1", color: "white", borderColor: "#6366F1" }
                    : { background: "white", color: "#6b7280", borderColor: "#e5e7eb" }
                }
              >
                {t === "PERCENT" ? "Phần trăm (%)" : "Số tiền cố định (đ)"}
              </button>
            ))}
          </div>
        </div>

        <div>
          <label
            htmlFor="admin-coupon-value"
            className="block text-sm font-semibold text-gray-700 mb-1.5"
          >
            Giá trị {type === "PERCENT" ? "(%)" : "(VND)"}
          </label>
          <input
            id="admin-coupon-value"
            value={value}
            onChange={(e) => setValue(e.target.value)}
            placeholder={type === "PERCENT" ? "10" : "50000"}
            className="w-full px-3 py-2.5 border border-gray-200 rounded-xl text-sm outline-none focus:border-[#6366F1]"
          />
        </div>

        <div className="grid grid-cols-2 gap-3">
          <div>
            <label
              htmlFor="admin-coupon-min-order"
              className="block text-sm font-semibold text-gray-700 mb-1.5"
            >
              Đơn tối thiểu (VND)
            </label>
            <input
              id="admin-coupon-min-order"
              value={minOrderValue}
              onChange={(e) => setMinOrderValue(e.target.value)}
              placeholder="200000"
              className="w-full px-3 py-2.5 border border-gray-200 rounded-xl text-sm outline-none focus:border-[#6366F1]"
            />
          </div>
          {type === "PERCENT" ? (
            <div>
              <label
                htmlFor="admin-coupon-max-discount"
                className="block text-sm font-semibold text-gray-700 mb-1.5"
              >
                Giảm tối đa (VND)
              </label>
              <input
                id="admin-coupon-max-discount"
                value={maxDiscount}
                onChange={(e) => setMaxDiscount(e.target.value)}
                placeholder="100000"
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
      toast.success("Đã tạo coupon");
      setShowCreate(false);
    },
    onError: (err) => toast.error(err instanceof ApiError ? err.message : "Không thể tạo coupon"),
  });

  const deactivate = useMutation({
    mutationFn: (id: string) => adminDeactivateCoupon(id),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["admin", "coupons"] });
      toast.success("Đã vô hiệu hoá coupon");
    },
    onError: (err) => toast.error(err instanceof ApiError ? err.message : "Không thể vô hiệu hoá"),
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
        <h2 className="text-xl font-bold text-gray-800">Quản lý coupon</h2>
        <button
          onClick={() => setShowCreate(true)}
          disabled={createCoupon.isPending}
          className="px-4 py-2 rounded-xl text-white text-sm font-semibold disabled:opacity-50"
          style={{ background: "#FF6200" }}
        >
          + Tạo coupon
        </button>
      </div>

      {couponsQuery.isLoading ? <p className="text-sm text-gray-400">Đang tải...</p> : null}
      {couponsQuery.error instanceof ApiError ? (
        <p className="text-sm text-red-500">{couponsQuery.error.message}</p>
      ) : null}

      <div className="bg-white rounded-2xl shadow-sm overflow-hidden">
        <table className="w-full">
          <thead style={{ background: "#f9fafb" }}>
            <tr>
              {["Mã", "Loại", "Giá trị", "Trạng thái", ""].map((h) => (
                <th key={h} className="px-4 py-3 text-xs font-semibold text-gray-500 text-left">
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
                    {c.active ? "Đang bật" : "Tạm dừng"}
                  </span>
                </td>
                <td className="px-4 py-3">
                  {c.active ? (
                    <button
                      onClick={() => deactivate.mutate(c.id)}
                      disabled={deactivate.isPending}
                      className="text-xs font-semibold text-red-500 disabled:opacity-50"
                    >
                      Vô hiệu hoá
                    </button>
                  ) : null}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {!couponsQuery.isLoading && coupons.length === 0 ? (
          <p className="px-5 py-8 text-sm text-gray-400 text-center">Chưa có coupon nào</p>
        ) : null}
      </div>
    </div>
  );
}

function DisputesQueue() {
  const qc = useQueryClient();
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
      toast.success("Đã giải quyết khiếu nại");
      setResolveFor(null);
    },
    onError: (err) => toast.error(err instanceof ApiError ? err.message : "Không thể giải quyết"),
  });

  const disputes = disputesQuery.data ?? [];

  return (
    <div className="space-y-5">
      <FormDialog
        open={!!resolveFor}
        title="Giải quyết khiếu nại"
        description={resolveFor ? `ID: ${resolveFor}` : undefined}
        submitLabel="Gửi quyết định"
        submitColor="#00BFB3"
        fields={[
          {
            key: "resolution",
            label: "Hướng giải quyết",
            placeholder: "VD: Hoàn tiền 50% và xin lỗi khách...",
            type: "textarea",
            required: true,
          },
          {
            key: "refundAmount",
            label: "Số tiền hoàn (VND)",
            placeholder: "100000",
            type: "number",
            required: false,
            helper: "Để trống nếu không hoàn tiền.",
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
      <h2 className="text-xl font-bold text-gray-800">Khiếu nại</h2>

      {disputesQuery.isLoading ? <p className="text-sm text-gray-400">Đang tải...</p> : null}
      {!disputesQuery.isLoading && disputes.length === 0 ? (
        <div className="bg-white rounded-2xl p-8 text-center shadow-sm">
          <p className="text-sm text-gray-500">Không có khiếu nại nào đang mở</p>
        </div>
      ) : null}

      <div className="space-y-3">
        {disputes.map((d) => (
          <div key={d.id} className="bg-white rounded-2xl p-5 shadow-sm">
            <div className="flex items-start justify-between gap-3 mb-3">
              <div>
                <p className="text-xs font-mono text-gray-400">{d.id}</p>
                <p className="text-sm font-semibold text-gray-800">Đơn hàng: {d.returnId}</p>
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
              Giải quyết
            </button>
          </div>
        ))}
      </div>
    </div>
  );
}

function PayoutsQueue() {
  const qc = useQueryClient();
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
      toast.success("Đã đánh dấu hoàn thành");
    },
    onError: (err) => toast.error(err instanceof ApiError ? err.message : "Không thể cập nhật"),
  });

  const fail = useMutation({
    mutationFn: ({ id, reason }: { id: string; reason: string }) => adminFailPayout(id, { reason }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["admin", "payouts"] });
      toast.success("Đã đánh dấu thất bại");
      setFailFor(null);
    },
    onError: (err) => toast.error(err instanceof ApiError ? err.message : "Không thể cập nhật"),
  });

  const payouts = payoutsQuery.data ?? [];

  return (
    <div className="space-y-5">
      <FormDialog
        open={!!failFor}
        title="Đánh dấu yêu cầu rút tiền thất bại"
        description={failFor ? `Yêu cầu: ${failFor}` : undefined}
        submitLabel="Xác nhận"
        submitColor="#EF4444"
        fields={[
          {
            key: "reason",
            label: "Lý do",
            placeholder: "Sai số tài khoản, ngân hàng từ chối...",
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
      <h2 className="text-xl font-bold text-gray-800">Yêu cầu rút tiền</h2>

      {payoutsQuery.isLoading ? <p className="text-sm text-gray-400">Đang tải...</p> : null}
      {!payoutsQuery.isLoading && payouts.length === 0 ? (
        <div className="bg-white rounded-2xl p-8 text-center shadow-sm">
          <p className="text-sm text-gray-500">Không có yêu cầu rút tiền nào</p>
        </div>
      ) : null}

      <div className="bg-white rounded-2xl shadow-sm overflow-hidden">
        <div className="divide-y divide-gray-50">
          {payouts.map((p) => (
            <div key={p.id} className="px-5 py-4 flex items-center justify-between gap-4">
              <div>
                <p className="text-xs font-mono text-gray-400">{p.id}</p>
                <p className="text-sm font-semibold text-gray-800">Seller: {p.sellerId}</p>
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
                  Hoàn thành
                </button>
                <button
                  onClick={() => setFailFor(p.id)}
                  disabled={fail.isPending}
                  className="px-3 py-1.5 rounded-lg text-xs font-semibold border border-red-200 text-red-500 disabled:opacity-50"
                >
                  Thất bại
                </button>
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

const NAV_ITEMS: { id: AdminTab; label: string; icon: typeof LayoutDashboard }[] = [
  { id: "dashboard", label: "Tổng quan", icon: LayoutDashboard },
  { id: "sellers", label: "Duyệt Seller", icon: Users },
  { id: "reviews", label: "Kiểm duyệt", icon: Star },
  { id: "coupons", label: "Coupon", icon: Tag },
  { id: "disputes", label: "Khiếu nại", icon: AlertCircle },
  { id: "payouts", label: "Rút tiền", icon: Wallet },
];

export function AdminPage() {
  const [activeTab, setActiveTab] = useState<AdminTab>("dashboard");

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
              <h1 className="text-lg font-bold text-gray-800">Admin Console</h1>
              <p className="text-sm text-gray-500">VNShop Marketplace</p>
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
                    <span className="flex-1">{item.label}</span>
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
                <item.icon size={14} /> {item.label}
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
