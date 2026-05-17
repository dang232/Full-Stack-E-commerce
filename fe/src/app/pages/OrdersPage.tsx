import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Package,
  Truck,
  CheckCircle,
  XCircle,
  Clock,
  RefreshCw,
  MapPin,
  MessageSquare,
  Star,
  RotateCcw,
  AlertCircle,
  LogIn,
  ArrowLeftRight,
} from "lucide-react";
import { motion } from "motion/react";
import { useMemo, useState } from "react";
import { useNavigate } from "react-router";
import { toast } from "sonner";

import { ImageWithFallback } from "../components/image-with-fallback";
import { Modal } from "../components/ui/modal";
import { type Order as UIOrder } from "../components/vnshop-data";
import { useAuth } from "../hooks/use-auth";
import { useCart } from "../hooks/use-cart";
import { useCancelOrder, useMyOrders } from "../hooks/use-orders";
import { requestReturn } from "../lib/api/endpoints/orders";
import { getTracking } from "../lib/api/endpoints/shipping";
import { ApiError } from "../lib/api/envelope";
import { TRACKING_STEPS_FALLBACK } from "../lib/domain-constants";
import { parseOrderStatus } from "../lib/domain-enums";
import { formatPrice } from "../lib/format";
import type { Order as ServerOrder } from "../types/api";

type OrderTab = "all" | "pending" | "confirmed" | "shipping" | "delivered" | "cancelled";

const STATUS_CONFIG: Record<
  UIOrder["status"],
  { label: string; icon: typeof Package; color: string; bg: string }
> = {
  pending: { label: "Chờ xác nhận", icon: Clock, color: "#F59E0B", bg: "#FEF3C7" },
  confirmed: { label: "Đã xác nhận", icon: CheckCircle, color: "#3B82F6", bg: "#EFF6FF" },
  shipping: { label: "Đang giao hàng", icon: Truck, color: "#00BFB3", bg: "rgba(0,191,179,0.08)" },
  delivered: { label: "Đã giao hàng", icon: CheckCircle, color: "#10B981", bg: "#ECFDF5" },
  cancelled: { label: "Đã hủy", icon: XCircle, color: "#EF4444", bg: "#FEF2F2" },
  returned: { label: "Đã hoàn hàng", icon: RotateCcw, color: "#8B5CF6", bg: "#F5F3FF" },
};

function fromServer(o: ServerOrder): UIOrder {
  const sub = o.subOrders?.[0];
  const items =
    o.subOrders?.flatMap((s) =>
      (s.items ?? []).map((i) => ({
        productId: i.productId,
        name: i.name ?? i.productId,
        image: i.image ?? "",
        quantity: i.quantity,
        price: i.price,
      })),
    ) ?? [];
  return {
    id: o.id,
    date: o.createdAt ?? "",
    status: parseOrderStatus(o.status),
    items,
    total: o.total,
    shipping: o.shippingFee ?? 0,
    discount: o.discount ?? 0,
    address: o.address ? [o.address.line1, o.address.city].filter(Boolean).join(", ") : "",
    trackingCode: sub?.trackingCode ?? undefined,
    carrier: sub?.carrier ?? undefined,
    seller: sub?.sellerName ?? "",
    paymentMethod: o.paymentMethod ?? "",
    estimatedDelivery: o.estimatedDelivery ?? undefined,
  };
}

function TrackingModal({ order, onClose }: { order: UIOrder; onClose: () => void }) {
  // Real tracking is only fetchable when the order has both a tracking code
  // and a carrier — otherwise we degrade gracefully to the static timeline.
  const canFetch = !!(order.trackingCode && order.carrier);
  const tracking = useQuery({
    queryKey: ["shipping", "tracking", order.trackingCode, order.carrier],
    queryFn: () => getTracking(order.trackingCode ?? "", order.carrier ?? ""),
    enabled: canFetch,
    retry: false,
    staleTime: 30_000,
  });

  const events = tracking.data?.events ?? [];
  const showRealTimeline = canFetch && tracking.isSuccess && events.length > 0;
  const completedThrough =
    order.status === "delivered"
      ? TRACKING_STEPS_FALLBACK.length
      : order.status === "shipping"
        ? 4
        : order.status === "confirmed"
          ? 2
          : 1;

  return (
    <Modal
      open
      onClose={onClose}
      title="Theo dõi đơn hàng"
      subtitle={<span className="font-mono">{order.trackingCode ?? order.id}</span>}
    >
      {canFetch && tracking.isLoading ? (
        <div className="space-y-3" aria-label="Đang tải tracking">
          {Array.from({ length: 4 }).map((_, i) => (
            <div key={i} className="flex gap-4">
              <div className="w-6 h-6 rounded-full bg-gray-100" />
              <div className="flex-1 h-4 rounded bg-gray-100" />
            </div>
          ))}
        </div>
      ) : showRealTimeline ? (
        <div className="space-y-4">
          {events.map((ev, i) => (
            <div key={i} className="flex gap-4">
              <div className="flex flex-col items-center">
                <div
                  className="w-6 h-6 rounded-full flex items-center justify-center shrink-0"
                  style={{ background: i === 0 ? "#00BFB3" : "#9ca3af" }}
                >
                  <CheckCircle size={14} color="white" />
                </div>
                {i < events.length - 1 ? <div className="w-0.5 h-8 mt-1 bg-gray-200" /> : null}
              </div>
              <div className="pb-4 flex-1">
                <p className="text-sm font-medium text-gray-800">{ev.status ?? "Cập nhật"}</p>
                {ev.location ? <p className="text-xs text-gray-500 mt-0.5">{ev.location}</p> : null}
                {ev.note ? <p className="text-xs text-gray-500 mt-0.5">{ev.note}</p> : null}
                {ev.at ? <p className="text-[11px] text-gray-400 mt-1">{ev.at}</p> : null}
              </div>
            </div>
          ))}
        </div>
      ) : (
        <div className="space-y-4">
          {TRACKING_STEPS_FALLBACK.map((label, i) => {
            const done = i < completedThrough;
            return (
              <div key={i} className="flex gap-4">
                <div className="flex flex-col items-center">
                  <div
                    className="w-6 h-6 rounded-full flex items-center justify-center shrink-0"
                    style={{ background: done ? "#00BFB3" : "#e5e7eb" }}
                  >
                    {done ? (
                      <CheckCircle size={14} color="white" />
                    ) : (
                      <div className="w-2 h-2 rounded-full bg-gray-400" />
                    )}
                  </div>
                  {i < TRACKING_STEPS_FALLBACK.length - 1 ? (
                    <div
                      className="w-0.5 h-8 mt-1"
                      style={{ background: done ? "#00BFB3" : "#e5e7eb" }}
                    />
                  ) : null}
                </div>
                <div className="pb-4">
                  <p className={`text-sm font-medium ${done ? "text-gray-800" : "text-gray-400"}`}>
                    {label}
                  </p>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {(showRealTimeline ? tracking.data?.estimatedDelivery : order.estimatedDelivery) ? (
        <div
          className="mt-2 p-3 rounded-xl flex items-center gap-2 text-sm"
          style={{ background: "rgba(0,191,179,0.08)" }}
        >
          <MapPin size={15} style={{ color: "#00BFB3" }} />
          <span className="text-gray-600">
            Dự kiến giao:{" "}
            <strong>
              {showRealTimeline ? tracking.data?.estimatedDelivery : order.estimatedDelivery}
            </strong>
          </span>
        </div>
      ) : null}

      {canFetch && tracking.isError ? (
        <p className="text-[11px] text-amber-600 mt-4 flex items-center gap-1.5">
          <AlertCircle size={12} /> Không lấy được trạng thái mới nhất từ đơn vị vận chuyển — đang
          hiển thị tiến trình ước tính.
        </p>
      ) : !canFetch ? (
        <p className="text-[11px] text-gray-400 mt-4 flex items-center gap-1.5">
          <AlertCircle size={12} /> Đơn hàng chưa có mã vận đơn — sẽ cập nhật khi người bán giao cho
          đơn vị vận chuyển.
        </p>
      ) : null}
    </Modal>
  );
}

function ReturnModal({
  order,
  onClose,
  onSubmit,
  isSubmitting,
}: {
  order: ServerOrder;
  onClose: () => void;
  onSubmit: (input: { subOrderId: string; reason: string }) => void;
  isSubmitting: boolean;
}) {
  const subOrders = order.subOrders ?? [];
  const [subOrderId, setSubOrderId] = useState(subOrders[0]?.id ?? "");
  const [reason, setReason] = useState("");

  const handleSubmit = () => {
    if (!subOrderId) {
      toast.error("Đơn hàng này không có gói hàng nào để trả lại.");
      return;
    }
    if (reason.trim().length < 10) {
      toast.error("Vui lòng mô tả lý do trả hàng (ít nhất 10 ký tự).");
      return;
    }
    onSubmit({ subOrderId, reason: reason.trim() });
  };

  return (
    <Modal
      open
      onClose={onClose}
      dismissDisabled={isSubmitting}
      title="Yêu cầu trả hàng"
      subtitle={<span className="font-mono">{order.id}</span>}
      footer={
        <>
          <button
            onClick={onClose}
            className="flex-1 py-2.5 rounded-xl border border-gray-200 text-sm font-semibold text-gray-600"
          >
            Huỷ
          </button>
          <button
            onClick={handleSubmit}
            disabled={isSubmitting}
            className="flex-1 py-2.5 rounded-xl text-white text-sm font-semibold disabled:opacity-50"
            style={{ background: "#FF6200" }}
          >
            {isSubmitting ? "Đang gửi..." : "Gửi yêu cầu"}
          </button>
        </>
      }
    >
      {subOrders.length > 1 ? (
        <div className="mb-4">
          <label className="block text-sm font-semibold text-gray-700 mb-2">
            Chọn gói hàng cần trả
          </label>
          <select
            value={subOrderId}
            onChange={(e) => setSubOrderId(e.target.value)}
            className="w-full px-3 py-2.5 border border-gray-200 rounded-xl text-sm outline-none focus:border-[#00BFB3] bg-white"
          >
            {subOrders.map((s) => (
              <option key={s.id} value={s.id}>
                {s.sellerName ?? s.id} — {s.status}
              </option>
            ))}
          </select>
        </div>
      ) : null}

      <label className="block text-sm font-semibold text-gray-700 mb-2">Lý do trả hàng</label>
      <textarea
        value={reason}
        onChange={(e) => setReason(e.target.value)}
        rows={4}
        placeholder="Vui lòng mô tả chi tiết để người bán xử lý nhanh hơn..."
        className="w-full px-3 py-2.5 border border-gray-200 rounded-xl text-sm outline-none focus:border-[#00BFB3] resize-none bg-white"
      />

      <div className="mt-4 rounded-xl bg-amber-50 border border-amber-200 p-3 text-xs text-amber-800 flex items-start gap-2">
        <AlertCircle size={14} className="shrink-0 mt-0.5" />
        <p>
          Sau khi gửi yêu cầu, người bán có thể xác nhận hoặc từ chối. Nếu không có phản hồi, bạn có
          thể leo lên thành khiếu nại từ trang chi tiết đơn hàng.
        </p>
      </div>
    </Modal>
  );
}

function OrderCard({
  order,
  rawOrder,
  onCancel,
  onReview,
  onReorder,
}: {
  order: UIOrder;
  rawOrder: ServerOrder;
  onCancel: (id: string) => void;
  onReview: (productId: string) => void;
  onReorder: (items: UIOrder["items"]) => void;
}) {
  const qc = useQueryClient();
  const [showTracking, setShowTracking] = useState(false);
  const [showReturn, setShowReturn] = useState(false);
  const config = STATUS_CONFIG[order.status];
  const StatusIcon = config.icon;

  const submitReturn = useMutation({
    mutationFn: (input: { subOrderId: string; reason: string }) =>
      requestReturn({
        orderId: rawOrder.id,
        subOrderId: input.subOrderId,
        reason: input.reason,
      }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["orders"] });
      void qc.invalidateQueries({ queryKey: ["returns"] });
      setShowReturn(false);
      toast.success("Đã gửi yêu cầu trả hàng");
    },
    onError: (err) =>
      toast.error(err instanceof ApiError ? err.message : "Không thể gửi yêu cầu trả hàng"),
  });

  return (
    <>
      {showTracking ? <TrackingModal order={order} onClose={() => setShowTracking(false)} /> : null}
      {showReturn ? (
        <ReturnModal
          order={rawOrder}
          onClose={() => setShowReturn(false)}
          onSubmit={(input) => submitReturn.mutate(input)}
          isSubmitting={submitReturn.isPending}
        />
      ) : null}
      <motion.div
        initial={{ opacity: 0, y: 10 }}
        animate={{ opacity: 1, y: 0 }}
        className="bg-white rounded-2xl shadow-sm overflow-hidden"
      >
        <div className="flex items-center justify-between px-5 py-3.5 border-b border-gray-100">
          <div className="flex items-center gap-2">
            <span className="text-xs font-medium text-gray-500">Mã đơn:</span>
            <span className="text-xs font-bold text-gray-700 font-mono">{order.id}</span>
          </div>
          <div
            className="flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium"
            style={{ background: config.bg, color: config.color }}
          >
            <StatusIcon size={12} />
            {config.label}
          </div>
        </div>

        <div className="p-5">
          {order.items.length === 0 ? (
            <p className="text-sm text-gray-400 italic mb-3">Đang tải chi tiết sản phẩm...</p>
          ) : null}
          {order.items.map((item, i) => (
            <div key={i} className="flex items-center gap-4 mb-3">
              <ImageWithFallback
                src={item.image ?? ""}
                alt={item.name}
                className="w-16 h-16 rounded-xl object-cover border border-gray-100"
              />
              <div className="flex-1 min-w-0">
                <p className="text-sm font-medium text-gray-800 line-clamp-2">{item.name}</p>
                <div className="flex items-center gap-2 mt-1">
                  <span className="text-xs text-gray-500">x{item.quantity}</span>
                  <span className="font-bold text-sm" style={{ color: "#FF6200" }}>
                    {formatPrice(item.price)}
                  </span>
                </div>
              </div>
            </div>
          ))}

          <div className="flex items-center justify-between pt-3 border-t border-gray-100">
            <div className="text-sm text-gray-500">
              {order.date ? <span>{order.date}</span> : null}
              {order.seller ? <span> · {order.seller}</span> : null}
            </div>
            <div className="text-right">
              <span className="text-xs text-gray-500">Tổng: </span>
              <span className="font-black" style={{ color: "#FF6200" }}>
                {formatPrice(order.total)}
              </span>
            </div>
          </div>
        </div>

        <div className="flex gap-2 px-5 pb-4">
          {order.status === "shipping" ? (
            <button
              onClick={() => setShowTracking(true)}
              className="flex-1 py-2.5 rounded-xl border text-sm font-medium flex items-center justify-center gap-2"
              style={{ borderColor: "#00BFB3", color: "#00BFB3" }}
            >
              <MapPin size={15} /> Theo dõi đơn hàng
            </button>
          ) : null}
          {order.status === "delivered" ? (
            <>
              <button
                onClick={() => onReorder(order.items)}
                className="flex-1 py-2.5 rounded-xl border text-sm font-medium flex items-center justify-center gap-2 border-gray-200 text-gray-600 hover:bg-gray-50"
              >
                <RefreshCw size={14} /> Mua lại
              </button>
              <button
                onClick={() => setShowReturn(true)}
                className="flex-1 py-2.5 rounded-xl border text-sm font-medium flex items-center justify-center gap-2 border-amber-200 text-amber-600 hover:bg-amber-50"
              >
                <ArrowLeftRight size={14} /> Trả hàng
              </button>
              <button
                onClick={() => {
                  const firstProduct = order.items[0]?.productId;
                  if (firstProduct) onReview(firstProduct);
                  else toast.info("Không tìm thấy sản phẩm để đánh giá");
                }}
                className="flex-1 py-2.5 rounded-xl text-sm font-medium flex items-center justify-center gap-2 text-white"
                style={{ background: "#FF6200" }}
              >
                <Star size={14} /> Đánh giá
              </button>
            </>
          ) : null}
          {order.status === "pending" ? (
            <button
              onClick={() => onCancel(order.id)}
              className="flex items-center gap-2 py-2.5 px-4 rounded-xl border border-red-200 text-red-500 text-sm font-medium hover:bg-red-50"
            >
              <XCircle size={14} /> Hủy đơn
            </button>
          ) : null}
          <button className="py-2.5 px-4 rounded-xl border border-gray-200 text-sm text-gray-600 flex items-center gap-1 hover:bg-gray-50">
            <MessageSquare size={14} /> Chat
          </button>
        </div>
      </motion.div>
    </>
  );
}

export function OrdersPage() {
  const navigate = useNavigate();
  const { ready, authenticated, login } = useAuth();
  const [activeTab, setActiveTab] = useState<OrderTab>("all");
  const ordersQuery = useMyOrders({ size: 50 });
  const cancelOrder = useCancelOrder();
  const { addItemAsync } = useCart();

  const handleReorder = async (items: UIOrder["items"]) => {
    if (items.length === 0) {
      toast.info("Đơn hàng này không có sản phẩm để thêm lại.");
      return;
    }
    let added = 0;
    for (const item of items) {
      try {
        await addItemAsync({ productId: item.productId, quantity: item.quantity });
        added += 1;
      } catch (err) {
        // Continue trying remaining items, but report the first failure.
        if (added === 0) {
          toast.error(
            err instanceof ApiError
              ? err.message
              : "Không thể thêm sản phẩm vào giỏ. Có thể sản phẩm đã hết hàng.",
          );
          return;
        }
      }
    }
    if (added > 0) {
      toast.success(`Đã thêm ${added} sản phẩm vào giỏ`);
      void navigate("/cart");
    }
  };

  const allOrders = useMemo(() => {
    const content = ordersQuery.data?.content ?? [];
    return content.map((server) => ({ ui: fromServer(server), raw: server }));
  }, [ordersQuery.data]);

  const filtered = useMemo(
    () => (activeTab === "all" ? allOrders : allOrders.filter((o) => o.ui.status === activeTab)),
    [allOrders, activeTab],
  );

  const tabs: { id: OrderTab; label: string }[] = [
    { id: "all", label: "Tất cả" },
    { id: "pending", label: "Chờ xác nhận" },
    { id: "shipping", label: "Đang giao" },
    { id: "delivered", label: "Đã giao" },
    { id: "cancelled", label: "Đã hủy" },
  ];

  const handleCancel = (id: string) => {
    cancelOrder.mutate(id, {
      onSuccess: () => toast.success("Đã huỷ đơn hàng"),
      onError: (err) =>
        toast.error(err instanceof ApiError ? err.message : "Không thể huỷ đơn hàng"),
    });
  };

  if (!ready) {
    return (
      <div className="max-w-3xl mx-auto px-4 py-24 text-center text-sm text-gray-500">
        Đang khởi tạo phiên đăng nhập...
      </div>
    );
  }

  if (!authenticated) {
    return (
      <div className="max-w-3xl mx-auto px-4 py-24 text-center">
        <Package size={64} className="mx-auto mb-6 text-gray-200" />
        <h2 className="text-xl font-bold text-gray-600 mb-3">Đăng nhập để xem đơn hàng</h2>
        <button
          onClick={() => login("/orders")}
          className="px-8 py-3 rounded-xl text-white font-semibold inline-flex items-center gap-2"
          style={{ background: "linear-gradient(135deg, #00BFB3, #009990)" }}
        >
          <LogIn size={16} /> Đăng nhập
        </button>
      </div>
    );
  }

  return (
    <div className="max-w-3xl mx-auto px-4 py-6">
      <h1
        className="text-2xl font-bold text-gray-800 mb-6"
        style={{ fontFamily: "'Be Vietnam Pro', sans-serif" }}
      >
        Đơn mua của tôi
      </h1>

      <div className="flex gap-1 overflow-x-auto pb-1 mb-6 scrollbar-hide">
        {tabs.map((tab) => (
          <button
            key={tab.id}
            onClick={() => setActiveTab(tab.id)}
            className="shrink-0 px-4 py-2 rounded-full text-sm font-medium transition-all"
            style={
              activeTab === tab.id
                ? { background: "#00BFB3", color: "#fff" }
                : { background: "#fff", color: "#6b7280", border: "1px solid #e5e7eb" }
            }
          >
            {tab.label}
          </button>
        ))}
      </div>

      <div className="space-y-4">
        {ordersQuery.isLoading ? (
          <div className="py-16 text-center text-sm text-gray-400">Đang tải đơn hàng...</div>
        ) : null}
        {ordersQuery.error && !ordersQuery.isLoading ? (
          <div className="py-16 text-center bg-white rounded-2xl">
            <AlertCircle size={48} className="mx-auto mb-4 text-red-300" />
            <p className="text-gray-600 font-medium mb-2">
              {ordersQuery.error instanceof ApiError
                ? ordersQuery.error.message
                : "Không thể tải đơn hàng"}
            </p>
            <button
              onClick={() => navigate("/")}
              className="text-sm font-medium"
              style={{ color: "#00BFB3" }}
            >
              Về trang chủ
            </button>
          </div>
        ) : null}
        {!ordersQuery.isLoading && !ordersQuery.error && filtered.length > 0
          ? filtered.map((entry) => (
              <OrderCard
                key={entry.ui.id}
                order={entry.ui}
                rawOrder={entry.raw}
                onCancel={handleCancel}
                onReview={(productId) => navigate(`/product/${productId}`)}
                onReorder={(items) => void handleReorder(items)}
              />
            ))
          : null}
        {!ordersQuery.isLoading && !ordersQuery.error && filtered.length === 0 ? (
          <div className="py-16 text-center bg-white rounded-2xl">
            <Package size={48} className="mx-auto mb-4 text-gray-200" />
            <p className="text-gray-500 font-medium">Không có đơn hàng nào</p>
          </div>
        ) : null}
      </div>
    </div>
  );
}
