import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  LayoutDashboard,
  Package,
  ShoppingBag,
  Star,
  Wallet,
  MessageSquare,
  Settings,
  Bell,
  Plus,
  Eye,
  Edit,
  CheckCircle,
  Truck,
  ArrowUpRight,
  Search,
  Filter,
  AlertCircle,
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
import { SellerProductModal } from "../components/seller-product-modal";
import { Modal } from "../components/ui/modal";
import { type Product } from "../components/vnshop-data";
import { useProducts } from "../hooks/use-products";
import { useSellerRevenue } from "../hooks/use-seller-revenue";
import {
  sellerAcceptOrder,
  sellerPendingOrders,
  sellerRejectOrder,
  sellerShipOrder,
  type PendingSubOrder,
} from "../lib/api/endpoints/orders";
import type { SellerRevenuePoint } from "../lib/api/endpoints/seller-analytics";
import {
  myPayouts,
  myWallet,
  requestPayout,
  type Payout,
} from "../lib/api/endpoints/seller-finance";
import { sellerProfile } from "../lib/api/endpoints/users";
import { ApiError } from "../lib/api/envelope";
import { formatPrice } from "../lib/format";

type SellerTab = "dashboard" | "products" | "orders" | "reviews" | "wallet" | "settings";

interface RevenueChartPoint {
  day: string;
  revenue: number;
  orders: number;
}

/**
 * Map an ISO date (YYYY-MM-DD) to a short Vietnamese weekday label so the
 * chart axis matches the legacy mock formatting.
 */
function dateToWeekdayLabel(iso: string): string {
  const labels = ["CN", "T2", "T3", "T4", "T5", "T6", "T7"];
  const d = new Date(`${iso}T00:00:00`);
  if (Number.isNaN(d.getTime())) return iso;
  return labels[d.getDay()] ?? iso;
}

function toChartData(points: SellerRevenuePoint[]): RevenueChartPoint[] {
  return points.map((p) => ({
    day: dateToWeekdayLabel(p.date),
    revenue: Number(p.revenue) || 0,
    orders: p.orderCount,
  }));
}

function KPICard({
  icon: Icon,
  label,
  value,
  change,
  color,
}: {
  icon: typeof LayoutDashboard;
  label: string;
  value: string;
  change?: string;
  color: string;
}) {
  return (
    <div className="bg-white rounded-2xl p-5 shadow-sm">
      <div className="flex items-center justify-between mb-4">
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
    </div>
  );
}

function Dashboard({
  pendingOrders,
  walletBalance,
}: {
  pendingOrders: PendingSubOrder[];
  walletBalance: number | null;
}) {
  const { points, isLoading: revenueLoading, error: revenueError } = useSellerRevenue({ days: 30 });
  const chartData = useMemo(() => toChartData(points), [points]);
  const hasRevenue = chartData.length > 0;

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-xl font-bold text-gray-800">Tổng quan</h2>
          <p className="text-sm text-gray-500">Dữ liệu thời gian thực</p>
        </div>
      </div>

      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <KPICard
          icon={Wallet}
          label="Số dư ví"
          value={walletBalance !== null ? formatPrice(walletBalance) : "—"}
          color="#00BFB3"
        />
        <KPICard
          icon={ShoppingBag}
          label="Đơn cần xử lý"
          value={String(pendingOrders.length)}
          color="#FF6200"
        />
        <KPICard icon={Eye} label="Lượt xem shop" value="—" color="#3B82F6" />
        <KPICard icon={Star} label="Điểm đánh giá" value="—" color="#F59E0B" />
      </div>

      <div className="bg-white rounded-2xl p-5 shadow-sm">
        <div className="flex items-center justify-between mb-5">
          <h3 className="font-bold text-gray-800">Doanh thu 30 ngày</h3>
          <span className="text-[11px] text-gray-400">
            Cập nhật theo dữ liệu đơn của bạn
          </span>
        </div>
        {revenueError instanceof ApiError ? (
          <div className="rounded-xl bg-red-50 border border-red-200 px-4 py-3 text-sm text-red-600 mb-3">
            Không tải được doanh thu: {revenueError.message}
          </div>
        ) : null}
        {revenueLoading ? (
          <p className="text-sm text-gray-400 py-12 text-center">Đang tải doanh thu...</p>
        ) : hasRevenue ? (
          <ResponsiveContainer width="100%" height={200}>
            <AreaChart data={chartData}>
              <defs>
                <linearGradient id="revenueGrad" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%" stopColor="#00BFB3" stopOpacity={0.2} />
                  <stop offset="95%" stopColor="#00BFB3" stopOpacity={0} />
                </linearGradient>
              </defs>
              <CartesianGrid strokeDasharray="3 3" stroke="#f3f4f6" />
              <XAxis
                dataKey="day"
                axisLine={false}
                tickLine={false}
                tick={{ fill: "#9ca3af", fontSize: 12 }}
              />
              <YAxis
                axisLine={false}
                tickLine={false}
                tick={{ fill: "#9ca3af", fontSize: 11 }}
                tickFormatter={(v) => `${(v / 1000000).toFixed(0)}tr`}
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
                dataKey="revenue"
                stroke="#00BFB3"
                strokeWidth={2.5}
                fill="url(#revenueGrad)"
              />
            </AreaChart>
          </ResponsiveContainer>
        ) : (
          <p className="text-sm text-gray-400 py-12 text-center">
            Chưa có dữ liệu doanh thu trong 30 ngày qua.
          </p>
        )}
      </div>

      <div className="bg-white rounded-2xl p-5 shadow-sm">
        <h3 className="font-bold text-gray-800 mb-4">Số đơn 30 ngày</h3>
        {revenueLoading ? (
          <p className="text-sm text-gray-400 py-10 text-center">Đang tải...</p>
        ) : hasRevenue ? (
          <ResponsiveContainer width="100%" height={160}>
            <BarChart data={chartData}>
              <CartesianGrid strokeDasharray="3 3" stroke="#f3f4f6" vertical={false} />
              <XAxis
                dataKey="day"
                axisLine={false}
                tickLine={false}
                tick={{ fill: "#9ca3af", fontSize: 12 }}
              />
              <YAxis axisLine={false} tickLine={false} tick={{ fill: "#9ca3af", fontSize: 11 }} />
              <Tooltip
                contentStyle={{
                  borderRadius: "12px",
                  border: "none",
                  boxShadow: "0 4px 24px rgba(0,0,0,0.1)",
                }}
              />
              <Bar dataKey="orders" fill="#FF6200" radius={[6, 6, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        ) : (
          <p className="text-sm text-gray-400 py-10 text-center">Chưa có đơn nào trong 30 ngày qua.</p>
        )}
      </div>
    </div>
  );
}

function ProductsManagement() {
  const [search, setSearch] = useState("");
  const [showCreate, setShowCreate] = useState(false);
  const [editing, setEditing] = useState<Product | null>(null);
  const { data: catalog = [], isLoading } = useProducts();
  const filtered = catalog.filter((p) => p.name.toLowerCase().includes(search.toLowerCase()));

  return (
    <div className="space-y-5">
      <SellerProductModal open={showCreate} onClose={() => setShowCreate(false)} />
      <SellerProductModal open={!!editing} product={editing} onClose={() => setEditing(null)} />
      <div className="flex items-center justify-between">
        <h2 className="text-xl font-bold text-gray-800">Quản lý sản phẩm</h2>
        <button
          onClick={() => setShowCreate(true)}
          className="flex items-center gap-2 px-4 py-2.5 rounded-xl text-white font-semibold text-sm"
          style={{ background: "#FF6200" }}
        >
          <Plus size={16} /> Thêm sản phẩm
        </button>
      </div>

      <div className="rounded-xl bg-amber-50 border border-amber-200 p-3 text-xs text-amber-800 flex items-start gap-2">
        <AlertCircle size={14} className="shrink-0 mt-0.5" />
        <p>
          Đang hiển thị toàn bộ catalog. API riêng cho seller (`/sellers/me/products`) sẽ thay thế
          khi sẵn sàng.
        </p>
      </div>

      <div className="flex gap-3">
        <div className="flex-1 flex items-center gap-3 bg-white border border-gray-200 rounded-xl px-4 py-2.5 shadow-sm">
          <Search size={16} className="text-gray-400" />
          <input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Tìm kiếm sản phẩm..."
            className="flex-1 text-sm outline-none"
          />
        </div>
        <button className="flex items-center gap-2 px-4 py-2.5 border border-gray-200 rounded-xl bg-white text-sm text-gray-600">
          <Filter size={15} /> Lọc
        </button>
      </div>

      {isLoading ? <p className="text-sm text-gray-400">Đang tải sản phẩm...</p> : null}

      <div className="bg-white rounded-2xl shadow-sm overflow-hidden">
        <table className="w-full">
          <thead style={{ background: "#f9fafb" }}>
            <tr>
              {["Sản phẩm", "Giá", "Kho", "Đã bán", ""].map((h) => (
                <th key={h} className="px-4 py-3 text-xs font-semibold text-gray-500 text-left">
                  {h}
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-50">
            {filtered.slice(0, 50).map((p) => (
              <tr key={p.id} className="hover:bg-gray-50 transition-colors">
                <td className="px-4 py-3">
                  <div className="flex items-center gap-3">
                    {p.image ? (
                      <img
                        src={p.image}
                        alt={p.name}
                        className="w-10 h-10 rounded-lg object-cover"
                        loading="lazy"
                      />
                    ) : null}
                    <p className="text-sm font-medium text-gray-800 max-w-[280px] truncate">
                      {p.name}
                    </p>
                  </div>
                </td>
                <td className="px-4 py-3 text-sm font-bold" style={{ color: "#FF6200" }}>
                  {formatPrice(p.price)}
                </td>
                <td className="px-4 py-3 text-sm text-gray-600">{p.stock}</td>
                <td className="px-4 py-3 text-sm text-gray-600">{p.sold.toLocaleString()}</td>
                <td className="px-4 py-3">
                  <button
                    onClick={() => setEditing(p)}
                    className="p-1.5 rounded-lg hover:bg-blue-50 text-blue-500 transition-colors"
                    title="Sửa sản phẩm"
                  >
                    <Edit size={14} />
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function ShipDialog({
  subOrderId,
  onClose,
  onSubmit,
  isSubmitting,
}: {
  subOrderId: string | null;
  onClose: () => void;
  onSubmit: (input: { carrier: string; trackingNumber: string }) => void;
  isSubmitting: boolean;
}) {
  if (!subOrderId) return null;
  return (
    <ShipDialogBody
      subOrderId={subOrderId}
      onClose={onClose}
      onSubmit={onSubmit}
      isSubmitting={isSubmitting}
    />
  );
}

function ShipDialogBody({
  subOrderId,
  onClose,
  onSubmit,
  isSubmitting,
}: {
  subOrderId: string;
  onClose: () => void;
  onSubmit: (input: { carrier: string; trackingNumber: string }) => void;
  isSubmitting: boolean;
}) {
  const [carrier, setCarrier] = useState("GHN");
  const [trackingNumber, setTrackingNumber] = useState("");

  const carriers = ["GHN", "GHTK", "VNPost", "J&T", "Khác"];

  const handleSubmit = () => {
    if (!carrier.trim() || carrier === "Khác") {
      toast.error("Vui lòng nhập tên đơn vị vận chuyển");
      return;
    }
    if (!trackingNumber.trim()) {
      toast.error("Vui lòng nhập mã vận đơn");
      return;
    }
    onSubmit({ carrier: carrier.trim(), trackingNumber: trackingNumber.trim() });
  };

  return (
    <Modal
      open
      onClose={onClose}
      dismissDisabled={isSubmitting}
      title="Bàn giao đơn cho vận chuyển"
      subtitle={<span className="font-mono">Sub-order: {subOrderId}</span>}
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
            style={{ background: "#FF6200" }}
          >
            {isSubmitting ? "Đang gửi..." : "Bàn giao"}
          </button>
        </>
      }
    >
      <div className="space-y-4">
        <div>
          <span className="block text-sm font-semibold text-gray-700 mb-2">Đơn vị vận chuyển</span>
          <div className="flex flex-wrap gap-2">
            {carriers.map((c) => (
              <button
                key={c}
                type="button"
                onClick={() => setCarrier(c)}
                className="px-3 py-1.5 rounded-lg text-sm font-medium border transition-colors"
                style={
                  carrier === c
                    ? { background: "#00BFB3", color: "white", borderColor: "#00BFB3" }
                    : { background: "white", color: "#6b7280", borderColor: "#e5e7eb" }
                }
              >
                {c}
              </button>
            ))}
          </div>
          {carrier === "Khác" ? (
            <input
              value={carrier === "Khác" ? "" : carrier}
              onChange={(e) => setCarrier(e.target.value)}
              placeholder="Tên đơn vị vận chuyển"
              className="mt-2 w-full px-3 py-2 border border-gray-200 rounded-lg text-sm outline-none focus:border-[#00BFB3]"
            />
          ) : null}
        </div>

        <div>
          <label
            htmlFor="seller-tracking-number"
            className="block text-sm font-semibold text-gray-700 mb-1.5"
          >
            Mã vận đơn
          </label>
          <input
            id="seller-tracking-number"
            value={trackingNumber}
            onChange={(e) => setTrackingNumber(e.target.value)}
            placeholder="VD: GHN1234567890"
            className="w-full px-3 py-2.5 border border-gray-200 rounded-xl text-sm outline-none focus:border-[#00BFB3]"
            // eslint-disable-next-line jsx-a11y/no-autofocus -- inside a modal opened by explicit user click; focusing the first input is expected UX
            autoFocus
          />
        </div>
      </div>
    </Modal>
  );
}

function OrdersManagement({
  orders,
  isLoading,
  error,
}: {
  orders: PendingSubOrder[];
  isLoading: boolean;
  error: unknown;
}) {
  const qc = useQueryClient();
  const [shipFor, setShipFor] = useState<string | null>(null);
  const [rejectFor, setRejectFor] = useState<string | null>(null);

  const accept = useMutation({
    mutationFn: (subOrderId: string) => sellerAcceptOrder(subOrderId),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["seller", "pending-orders"] });
      toast.success("Đã xác nhận đơn");
    },
    onError: (err) => toast.error(err instanceof ApiError ? err.message : "Không thể xác nhận đơn"),
  });

  const reject = useMutation({
    mutationFn: ({ id, reason }: { id: string; reason: string }) =>
      sellerRejectOrder(id, { reason }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["seller", "pending-orders"] });
      toast.success("Đã từ chối đơn");
      setRejectFor(null);
    },
    onError: (err) => toast.error(err instanceof ApiError ? err.message : "Không thể từ chối đơn"),
  });

  const ship = useMutation({
    mutationFn: ({ id, body }: { id: string; body: { carrier: string; trackingNumber: string } }) =>
      sellerShipOrder(id, body),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["seller", "pending-orders"] });
      toast.success("Đã chuyển sang trạng thái Giao hàng");
      setShipFor(null);
    },
    onError: (err) => toast.error(err instanceof ApiError ? err.message : "Không thể giao hàng"),
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
        title="Từ chối đơn"
        description={rejectFor ? `Đơn: ${rejectFor}` : undefined}
        submitLabel="Từ chối đơn"
        submitColor="#EF4444"
        fields={[
          {
            key: "reason",
            label: "Lý do từ chối",
            placeholder: "Hết hàng, không thể giao đến địa chỉ...",
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
      <h2 className="text-xl font-bold text-gray-800">Quản lý đơn hàng</h2>

      {isLoading ? <p className="text-sm text-gray-400">Đang tải đơn hàng...</p> : null}
      {error instanceof ApiError ? (
        <p className="text-sm text-red-500">Không tải được đơn hàng: {error.message}</p>
      ) : null}
      {!isLoading && orders.length === 0 && !error ? (
        <div className="bg-white rounded-2xl p-8 text-center shadow-sm">
          <Package size={40} className="mx-auto mb-3 text-gray-300" />
          <p className="text-sm text-gray-500">Không có đơn hàng nào cần xử lý</p>
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
                  <p className="text-sm text-gray-500 mt-0.5">Mã đơn cha: {order.orderId}</p>
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
                        <CheckCircle size={13} /> Xác nhận
                      </button>
                      <button
                        onClick={() => setRejectFor(order.id)}
                        disabled={reject.isPending}
                        className="flex items-center gap-1 px-3 py-1.5 rounded-lg text-xs font-semibold border border-red-200 text-red-500 disabled:opacity-50"
                      >
                        Từ chối
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
                      <Truck size={13} /> Giao hàng
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

function WalletTab({
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
  const [showPayoutDialog, setShowPayoutDialog] = useState(false);
  const requestPayoutMutation = useMutation({
    mutationFn: (body: { amount: number; bankAccount: string }) => requestPayout(body),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["seller", "wallet"] });
      void qc.invalidateQueries({ queryKey: ["seller", "payouts"] });
      toast.success("Đã gửi yêu cầu rút tiền");
      setShowPayoutDialog(false);
    },
    onError: (err) => toast.error(err instanceof ApiError ? err.message : "Không thể gửi yêu cầu"),
  });

  return (
    <div className="space-y-5">
      <FormDialog
        open={showPayoutDialog}
        title="Yêu cầu rút tiền"
        description={balance !== null ? `Số dư hiện có: ${formatPrice(balance)}` : undefined}
        submitLabel="Gửi yêu cầu"
        submitColor="#00BFB3"
        fields={[
          {
            key: "amount",
            label: "Số tiền (VND)",
            placeholder: "1000000",
            type: "number",
            required: true,
          },
          {
            key: "bankAccount",
            label: "Số tài khoản ngân hàng",
            placeholder: "VD: VCB - 0123456789",
            required: true,
          },
        ]}
        onClose={() => setShowPayoutDialog(false)}
        onSubmit={({ amount, bankAccount }) => {
          const parsed = Number(amount.replace(/\D/g, ""));
          if (!parsed || parsed <= 0) {
            toast.error("Số tiền không hợp lệ");
            return;
          }
          if (balance !== null && parsed > balance) {
            toast.error("Số tiền vượt quá số dư khả dụng");
            return;
          }
          requestPayoutMutation.mutate({ amount: parsed, bankAccount });
        }}
        isSubmitting={requestPayoutMutation.isPending}
      />
      <h2 className="text-xl font-bold text-gray-800">Ví & Thanh toán</h2>

      <div
        className="rounded-2xl p-6 text-white"
        style={{ background: "linear-gradient(135deg, #00BFB3, #006b65)" }}
      >
        <p className="text-white/70 text-sm mb-2">Số dư khả dụng</p>
        <p className="text-4xl font-black mb-4">
          {balance !== null ? formatPrice(balance) : isLoading ? "Đang tải..." : "—"}
        </p>
        <div className="flex gap-3">
          <button
            onClick={() => setShowPayoutDialog(true)}
            disabled={requestPayoutMutation.isPending || balance === null || balance <= 0}
            className="px-5 py-2.5 rounded-xl bg-white/20 font-semibold text-sm hover:bg-white/30 transition-colors disabled:opacity-50"
          >
            {requestPayoutMutation.isPending ? "Đang gửi..." : "Rút tiền"}
          </button>
        </div>
      </div>

      {error instanceof ApiError ? (
        <p className="text-sm text-red-500">Không tải được ví: {error.message}</p>
      ) : null}

      <div className="bg-white rounded-2xl shadow-sm overflow-hidden">
        <h3 className="px-5 py-4 font-bold text-gray-800 border-b border-gray-100">
          Lịch sử rút tiền
        </h3>
        {payouts.length === 0 ? (
          <p className="px-5 py-8 text-sm text-gray-400 text-center">
            Chưa có yêu cầu rút tiền nào
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

const NAV_ITEMS: { id: SellerTab; label: string; icon: typeof LayoutDashboard }[] = [
  { id: "dashboard", label: "Tổng quan", icon: LayoutDashboard },
  { id: "products", label: "Sản phẩm", icon: Package },
  { id: "orders", label: "Đơn hàng", icon: ShoppingBag },
  { id: "reviews", label: "Đánh giá", icon: Star },
  { id: "wallet", label: "Ví tiền", icon: Wallet },
  { id: "settings", label: "Cài đặt", icon: Settings },
];

export function SellerPage() {
  const [activeTab, setActiveTab] = useState<SellerTab>("dashboard");

  const profileQuery = useQuery({
    queryKey: ["seller", "profile"],
    queryFn: sellerProfile,
    retry: false,
  });

  const pendingQuery = useQuery({
    queryKey: ["seller", "pending-orders"],
    queryFn: sellerPendingOrders,
    refetchInterval: 60_000,
    retry: false,
  });

  const walletQuery = useQuery({
    queryKey: ["seller", "wallet"],
    queryFn: myWallet,
    retry: false,
  });

  const payoutsQuery = useQuery({
    queryKey: ["seller", "payouts"],
    queryFn: myPayouts,
    retry: false,
  });

  const pendingOrders = useMemo(() => pendingQuery.data ?? [], [pendingQuery.data]);
  const balance = walletQuery.data?.balance ?? null;
  const sellerName = profileQuery.data?.name ?? "Shop của tôi";

  return (
    <div className="min-h-screen" style={{ background: "#f4f6f9" }}>
      <div className="max-w-7xl mx-auto px-4 py-6">
        <div className="flex items-center justify-between mb-6 bg-white rounded-2xl p-5 shadow-sm">
          <div className="flex items-center gap-4">
            <div
              className="w-14 h-14 rounded-2xl flex items-center justify-center text-white font-bold text-lg"
              style={{ background: "linear-gradient(135deg, #00BFB3, #009990)" }}
            >
              {sellerName.charAt(0).toUpperCase()}
            </div>
            <div>
              <h1 className="text-lg font-bold text-gray-800">{sellerName}</h1>
              <div className="flex items-center gap-2 text-sm text-gray-500">
                <span className="flex items-center gap-1 text-green-500 font-medium">
                  <CheckCircle size={13} /> Đã đăng nhập
                </span>
                {pendingOrders.length > 0 ? (
                  <>
                    <span>·</span>
                    <span style={{ color: "#FF6200" }}>{pendingOrders.length} đơn cần xử lý</span>
                  </>
                ) : null}
              </div>
            </div>
          </div>
          <div className="flex gap-2">
            <button className="relative p-2.5 rounded-xl border border-gray-200 text-gray-500 hover:bg-gray-50">
              <Bell size={18} />
            </button>
          </div>
        </div>

        <div className="flex gap-6">
          <nav className="hidden md:block w-56 shrink-0">
            <div className="bg-white rounded-2xl shadow-sm overflow-hidden">
              {NAV_ITEMS.map((item) => (
                <button
                  key={item.id}
                  onClick={() => setActiveTab(item.id)}
                  className="w-full flex items-center gap-3 px-4 py-3.5 text-sm font-medium transition-all text-left border-b border-gray-50 last:border-0"
                  style={{
                    background: activeTab === item.id ? "rgba(0,191,179,0.08)" : "transparent",
                    color: activeTab === item.id ? "#00BFB3" : "#6b7280",
                    borderLeft:
                      activeTab === item.id ? "3px solid #00BFB3" : "3px solid transparent",
                  }}
                >
                  <item.icon size={18} />
                  {item.label}
                </button>
              ))}
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
                    ? { background: "#00BFB3", color: "white" }
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
              {activeTab === "dashboard" ? (
                <Dashboard pendingOrders={pendingOrders} walletBalance={balance} />
              ) : null}
              {activeTab === "products" ? <ProductsManagement /> : null}
              {activeTab === "orders" ? (
                <OrdersManagement
                  orders={pendingOrders}
                  isLoading={pendingQuery.isLoading}
                  error={pendingQuery.error}
                />
              ) : null}
              {activeTab === "reviews" ? (
                <div className="bg-white rounded-2xl p-8 text-center shadow-sm">
                  <MessageSquare size={48} className="mx-auto mb-4 text-gray-200" />
                  <p className="text-sm text-gray-500">
                    Inbox đánh giá sẽ được hỗ trợ khi backend cung cấp endpoint riêng.
                  </p>
                </div>
              ) : null}
              {activeTab === "wallet" ? (
                <WalletTab
                  balance={balance}
                  payouts={payoutsQuery.data ?? []}
                  isLoading={walletQuery.isLoading}
                  error={walletQuery.error}
                />
              ) : null}
              {activeTab === "settings" ? (
                <div className="bg-white rounded-2xl p-6 shadow-sm">
                  <h2 className="text-xl font-bold text-gray-800 mb-3">Cài đặt Shop</h2>
                  <p className="text-sm text-gray-500 mb-4">
                    Cập nhật thông tin shop sẽ khả dụng khi backend mở endpoint riêng cho seller
                    profile.
                  </p>
                  <div className="space-y-4 text-sm">
                    {profileQuery.data ? (
                      <pre className="bg-gray-50 rounded-xl p-3 text-[11px] overflow-auto">
                        {JSON.stringify(profileQuery.data, null, 2)}
                      </pre>
                    ) : null}
                    {profileQuery.error instanceof ApiError ? (
                      <p className="text-sm text-red-500">{profileQuery.error.message}</p>
                    ) : null}
                  </div>
                </div>
              ) : null}
            </motion.div>
          </div>
        </div>
      </div>
    </div>
  );
}
