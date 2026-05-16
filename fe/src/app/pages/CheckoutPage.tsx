import { useMemo, useRef, useState } from "react";
import { useNavigate } from "react-router";
import { motion, AnimatePresence } from "motion/react";
import { useQuery } from "@tanstack/react-query";
import { v4 as uuidv4 } from "uuid";
import { toast } from "sonner";
import {
  MapPin, Truck, CreditCard, CheckCircle, ChevronRight,
  ArrowLeft, Plus, Shield, Package, AlertCircle, LogIn,
} from "lucide-react";
import { useAuth } from "../hooks/use-auth";
import { useCart } from "../hooks/use-cart";
import { ApiError } from "../lib/api/envelope";
import {
  calculateCheckout,
  paymentMethods as fetchPaymentMethods,
  shippingOptions as fetchShippingOptions,
} from "../lib/api/endpoints/checkout";
import { placeOrder } from "../lib/api/endpoints/orders";
import { codConfirm, momoCreate, vnpayCreate } from "../lib/api/endpoints/payment";
import { myProfile } from "../lib/api/endpoints/users";
import { formatPrice } from "../lib/format";
import type { Address } from "../types/api";

type Step = "address" | "shipping" | "payment" | "review" | "success";

const STEPS: { id: Step; label: string; icon: typeof MapPin }[] = [
  { id: "address", label: "Địa chỉ", icon: MapPin },
  { id: "shipping", label: "Vận chuyển", icon: Truck },
  { id: "payment", label: "Thanh toán", icon: CreditCard },
  { id: "review", label: "Xác nhận", icon: CheckCircle },
];

interface ShippingOption {
  id: string;
  name: string;
  desc: string;
  fee: number;
  eta: string;
}

interface PaymentOption {
  id: "VNPAY" | "MOMO" | "COD" | "BANK";
  name: string;
  icon: string;
  desc: string;
}

const FALLBACK_SHIPPING: ShippingOption[] = [
  { id: "standard", name: "Giao Hàng Tiêu Chuẩn", desc: "Giao trong 1-2 ngày", fee: 30000, eta: "Ngày mai" },
  { id: "economy", name: "Giao Hàng Tiết Kiệm", desc: "Giao trong 3-5 ngày", fee: 20000, eta: "3-5 ngày" },
];

const FALLBACK_PAYMENT: PaymentOption[] = [
  { id: "VNPAY", name: "VNPay", icon: "💳", desc: "Thanh toán qua ví VNPay, QR Code" },
  { id: "MOMO", name: "MoMo", icon: "💜", desc: "Thanh toán qua ví MoMo" },
  { id: "BANK", name: "Thẻ ngân hàng", icon: "🏦", desc: "Visa, Mastercard, JCB" },
  { id: "COD", name: "Thanh toán khi nhận hàng", icon: "💵", desc: "Trả tiền mặt khi nhận hàng" },
];

function formatAddressLine(a: Address): string {
  return [a.line1, a.ward, a.district, a.city, a.province].filter(Boolean).join(", ");
}

export function CheckoutPage() {
  const navigate = useNavigate();
  const { ready, authenticated, login, profile } = useAuth();
  const { items: cartItems, totalAmount, isLoading: cartLoading, refetch: refetchCart } = useCart();

  // Profile + addresses (best-effort — backend may not return addresses yet).
  const profileQuery = useQuery({
    queryKey: ["users", "me"],
    queryFn: myProfile,
    enabled: ready && authenticated,
  });
  const addresses: Address[] = profileQuery.data?.addresses ?? [];

  const shippingQuery = useQuery({
    queryKey: ["checkout", "shipping-options", cartItems.map((i) => i.productId).join(",")],
    queryFn: () =>
      fetchShippingOptions({
        items: cartItems.map((i) => ({ productId: i.productId, quantity: i.quantity })),
      }),
    enabled: cartItems.length > 0,
    retry: false,
  });

  const paymentQuery = useQuery({
    queryKey: ["checkout", "payment-methods"],
    queryFn: fetchPaymentMethods,
    enabled: ready && authenticated,
    retry: false,
  });

  const shippingOptions: ShippingOption[] = useMemo(() => {
    const data = shippingQuery.data;
    if (!data || data.length === 0) return FALLBACK_SHIPPING;
    return data.map((s, i) => ({
      id: s.code ?? `option-${i}`,
      name: s.name ?? `Phương thức ${i + 1}`,
      desc: typeof s.estimatedDays === "number" ? `Giao trong ${s.estimatedDays} ngày` : "Tiêu chuẩn",
      fee: s.fee ?? 0,
      eta: typeof s.estimatedDays === "number" ? `${s.estimatedDays} ngày` : "Tiêu chuẩn",
    }));
  }, [shippingQuery.data]);

  const paymentOptions: PaymentOption[] = useMemo(() => {
    const data = paymentQuery.data;
    if (!data || data.length === 0) return FALLBACK_PAYMENT;
    const codeToFallback: Record<string, PaymentOption> = {
      VNPAY: FALLBACK_PAYMENT[0],
      MOMO: FALLBACK_PAYMENT[1],
      BANK: FALLBACK_PAYMENT[2],
      COD: FALLBACK_PAYMENT[3],
    };
    return data
      .filter((p) => p.enabled !== false)
      .map((p) => codeToFallback[p.code] ?? { id: p.code as PaymentOption["id"], name: p.name, icon: "💳", desc: p.description ?? "" });
  }, [paymentQuery.data]);

  const [step, setStep] = useState<Step>("address");
  const [selectedAddressIndex, setSelectedAddressIndex] = useState(0);
  // The user's explicit shipping pick. Empty string means "use default" — we resolve
  // to the first available option at render time, so we never need an effect to
  // mirror server-provided defaults into local state.
  const [shippingChoice, setShippingChoice] = useState<string>("");
  const selectedShippingId = shippingChoice || shippingOptions[0]?.id || "";
  const [selectedPaymentId, setSelectedPaymentId] = useState<PaymentOption["id"]>("VNPAY");
  const [note, setNote] = useState("");
  const [isProcessing, setIsProcessing] = useState(false);
  const [placedOrderId, setPlacedOrderId] = useState<string | null>(null);

  // Idempotency key generated once per checkout attempt; reused on retries.
  const idempotencyKeyRef = useRef<string>("");
  if (!idempotencyKeyRef.current) idempotencyKeyRef.current = uuidv4();

  // Server-side preview of totals — best effort. UI falls back to local sum if unavailable.
  const calcQuery = useQuery({
    queryKey: [
      "checkout",
      "calculate",
      cartItems.map((i) => `${i.productId}:${i.quantity}`).join(","),
      addresses[selectedAddressIndex]?.line1,
    ],
    queryFn: () =>
      calculateCheckout({
        items: cartItems.map((i) => ({ productId: i.productId, quantity: i.quantity })),
      }),
    enabled: cartItems.length > 0,
    retry: false,
  });

  const shipping = shippingOptions.find((m) => m.id === selectedShippingId) ?? shippingOptions[0];
  const subtotal = calcQuery.data?.subtotal ?? totalAmount;
  const shippingFee = calcQuery.data?.shippingFee ?? shipping?.fee ?? 0;
  const discount = calcQuery.data?.discount ?? 0;
  const finalTotal = calcQuery.data?.total ?? Math.max(0, subtotal - discount) + shippingFee;

  const stepOrder: Step[] = ["address", "shipping", "payment", "review", "success"];
  const stepIdx = stepOrder.indexOf(step);

  const buyerName =
    [profile?.firstName, profile?.lastName].filter(Boolean).join(" ") ||
    profile?.username ||
    profile?.email ||
    "Khách hàng";

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
        <h2 className="text-xl font-bold text-gray-600 mb-3">Đăng nhập để thanh toán</h2>
        <button
          onClick={() => login("/checkout")}
          className="px-8 py-3 rounded-xl text-white font-semibold inline-flex items-center gap-2"
          style={{ background: "linear-gradient(135deg, #00BFB3, #009990)" }}
        >
          <LogIn size={16} /> Đăng nhập
        </button>
      </div>
    );
  }

  if (cartLoading) {
    return (
      <div className="max-w-3xl mx-auto px-4 py-24 text-center text-sm text-gray-500">
        Đang tải giỏ hàng...
      </div>
    );
  }

  if (cartItems.length === 0 && step !== "success") {
    return (
      <div className="max-w-3xl mx-auto px-4 py-24 text-center">
        <Package size={56} className="mx-auto mb-4 text-gray-200" />
        <h2 className="text-xl font-bold text-gray-600 mb-3">Giỏ hàng trống</h2>
        <button
          onClick={() => navigate("/")}
          className="px-6 py-2.5 rounded-xl text-white font-medium"
          style={{ background: "#00BFB3" }}
        >
          Tiếp tục mua sắm
        </button>
      </div>
    );
  }

  const handlePlaceOrder = async () => {
    setIsProcessing(true);
    try {
      const order = await placeOrder(
        {
          items: cartItems.map((i) => ({ productId: i.productId, quantity: i.quantity })),
          paymentMethod: selectedPaymentId,
          notes: note || undefined,
          shippingChoices: shipping ? [{ sellerId: "_", code: shipping.id }] : undefined,
        },
        idempotencyKeyRef.current,
      );

      setPlacedOrderId(order.id);
      // Clear cart on the client — server typically clears too. Refetch to reconcile.
      void refetchCart();

      // Payment dispatch
      if (selectedPaymentId === "VNPAY" || selectedPaymentId === "MOMO") {
        try {
          const init =
            selectedPaymentId === "VNPAY"
              ? await vnpayCreate({
                  orderId: order.id,
                  returnUrl: `${window.location.origin}/payment/return/vnpay`,
                })
              : await momoCreate({
                  orderId: order.id,
                  returnUrl: `${window.location.origin}/payment/return/momo`,
                });
          window.location.href = init.redirectUrl;
          return;
        } catch (err) {
          toast.error(
            err instanceof ApiError
              ? `Không khởi tạo được thanh toán: ${err.message}`
              : "Không khởi tạo được thanh toán",
          );
          // Fall through to success screen — order exists; buyer can pay later.
        }
      } else if (selectedPaymentId === "COD") {
        try {
          await codConfirm({ orderId: order.id });
        } catch {
          // COD confirmation is best-effort; buyer will see status update later.
        }
      }

      setStep("success");
    } catch (err) {
      const message =
        err instanceof ApiError ? err.message : "Không thể đặt hàng. Vui lòng thử lại.";
      toast.error(message, {
        description:
          err instanceof ApiError && err.correlationId
            ? `Mã hỗ trợ: ${err.correlationId}`
            : undefined,
      });
    } finally {
      setIsProcessing(false);
    }
  };

  const handleNext = () => {
    if (step === "address") {
      if (addresses.length === 0) {
        toast.error("Vui lòng thêm ít nhất một địa chỉ trong trang Hồ sơ trước khi thanh toán.");
        navigate("/profile");
        return;
      }
      setStep("shipping");
      return;
    }
    if (step === "shipping") {
      if (!selectedShippingId) {
        toast.error("Vui lòng chọn phương thức vận chuyển");
        return;
      }
      setStep("payment");
      return;
    }
    if (step === "payment") {
      setStep("review");
      return;
    }
    if (step === "review") {
      void handlePlaceOrder();
    }
  };

  if (step === "success") {
    return (
      <div className="max-w-lg mx-auto px-4 py-16 text-center">
        <motion.div
          initial={{ scale: 0.5, opacity: 0 }}
          animate={{ scale: 1, opacity: 1 }}
          transition={{ type: "spring", duration: 0.6 }}
        >
          <div
            className="w-24 h-24 rounded-full flex items-center justify-center mx-auto mb-6"
            style={{ background: "rgba(0,191,179,0.12)" }}
          >
            <CheckCircle size={48} style={{ color: "#00BFB3" }} />
          </div>
          <h1
            className="text-3xl font-black text-gray-800 mb-3"
            style={{ fontFamily: "'Be Vietnam Pro', sans-serif" }}
          >
            Đặt hàng thành công! 🎉
          </h1>
          {placedOrderId && (
            <>
              <p className="text-gray-500 mb-2">Mã đơn hàng của bạn:</p>
              <div
                className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl mb-6 font-mono font-bold text-lg"
                style={{ background: "rgba(0,191,179,0.08)", color: "#00BFB3" }}
              >
                {placedOrderId}
              </div>
            </>
          )}
          {selectedPaymentId === "COD" && (
            <p className="text-sm text-gray-500 mb-2">
              Bạn sẽ thanh toán <strong>{formatPrice(finalTotal)}</strong> khi nhận hàng.
            </p>
          )}
          <div className="flex gap-3">
            <button
              onClick={() => navigate("/orders")}
              className="flex-1 py-3 rounded-xl border-2 font-semibold text-sm transition-colors"
              style={{ borderColor: "#00BFB3", color: "#00BFB3" }}
            >
              Xem đơn hàng
            </button>
            <button
              onClick={() => navigate("/")}
              className="flex-1 py-3 rounded-xl text-white font-semibold text-sm"
              style={{ background: "linear-gradient(135deg, #00BFB3, #009990)" }}
            >
              Tiếp tục mua sắm
            </button>
          </div>
        </motion.div>
      </div>
    );
  }

  return (
    <div className="max-w-5xl mx-auto px-4 py-6">
      <div className="flex items-center gap-3 mb-8">
        <button
          onClick={() => (step === "address" ? navigate("/cart") : setStep(stepOrder[stepIdx - 1]))}
          className="p-2 rounded-xl hover:bg-white text-gray-500"
        >
          <ArrowLeft size={20} />
        </button>
        <h1 className="text-xl font-bold text-gray-800">Thanh toán</h1>
      </div>

      <div className="flex items-center justify-center mb-10">
        {STEPS.map((s, i) => {
          const isActive = s.id === step;
          const isDone = stepOrder.indexOf(s.id) < stepIdx;
          return (
            <div key={s.id} className="flex items-center">
              <div className="flex flex-col items-center">
                <div
                  className="w-10 h-10 rounded-full flex items-center justify-center transition-all duration-300"
                  style={{
                    background: isDone ? "#00BFB3" : isActive ? "#FF6200" : "#e5e7eb",
                    color: isDone || isActive ? "white" : "#9ca3af",
                  }}
                >
                  {isDone ? <CheckCircle size={18} /> : <s.icon size={18} />}
                </div>
                <span
                  className={`text-xs mt-1 font-medium ${
                    isActive ? "text-gray-800" : isDone ? "text-[#00BFB3]" : "text-gray-400"
                  }`}
                >
                  {s.label}
                </span>
              </div>
              {i < STEPS.length - 1 && (
                <div
                  className="w-16 sm:w-24 h-0.5 mb-5 mx-1 transition-colors"
                  style={{ background: isDone ? "#00BFB3" : "#e5e7eb" }}
                />
              )}
            </div>
          );
        })}
      </div>

      <div className="grid lg:grid-cols-[1fr_340px] gap-6">
        <AnimatePresence mode="wait">
          <motion.div
            key={step}
            initial={{ opacity: 0, x: 20 }}
            animate={{ opacity: 1, x: 0 }}
            exit={{ opacity: 0, x: -20 }}
            transition={{ duration: 0.25 }}
          >
            {step === "address" && (
              <div className="space-y-4">
                <h2 className="font-bold text-gray-800 text-lg mb-4">Chọn địa chỉ giao hàng</h2>
                {profileQuery.isLoading && (
                  <p className="text-sm text-gray-400">Đang tải địa chỉ...</p>
                )}
                {!profileQuery.isLoading && addresses.length === 0 && (
                  <div className="bg-white rounded-2xl p-5 text-sm text-gray-500 flex items-start gap-3">
                    <AlertCircle size={18} className="text-orange-500 shrink-0 mt-0.5" />
                    <div>
                      <p className="font-semibold text-gray-700">Bạn chưa có địa chỉ nào</p>
                      <p className="mt-1">Hãy thêm địa chỉ giao hàng trong Hồ sơ trước khi thanh toán.</p>
                      <button
                        onClick={() => navigate("/profile")}
                        className="mt-3 text-sm font-semibold"
                        style={{ color: "#00BFB3" }}
                      >
                        Đến trang Hồ sơ →
                      </button>
                    </div>
                  </div>
                )}
                {addresses.map((addr, i) => (
                  <button
                    key={i}
                    onClick={() => setSelectedAddressIndex(i)}
                    className="w-full p-4 rounded-2xl border-2 text-left transition-all"
                    style={{
                      borderColor: selectedAddressIndex === i ? "#00BFB3" : "#e5e7eb",
                      background: selectedAddressIndex === i ? "rgba(0,191,179,0.04)" : "white",
                    }}
                  >
                    <div className="flex items-start justify-between gap-3">
                      <div>
                        <div className="flex items-center gap-2 mb-1">
                          <span className="font-semibold text-gray-800">{buyerName}</span>
                          {addr.phone && (
                            <>
                              <span className="text-gray-400 text-sm">|</span>
                              <span className="text-gray-600 text-sm">{addr.phone}</span>
                            </>
                          )}
                          {addr.isDefault && (
                            <span
                              className="px-1.5 py-0.5 rounded text-xs font-medium border"
                              style={{ borderColor: "#FF6200", color: "#FF6200" }}
                            >
                              Mặc định
                            </span>
                          )}
                        </div>
                        <p className="text-sm text-gray-500">{formatAddressLine(addr)}</p>
                      </div>
                      <div
                        className="w-5 h-5 rounded-full border-2 flex items-center justify-center shrink-0 mt-1 transition-all"
                        style={{ borderColor: selectedAddressIndex === i ? "#00BFB3" : "#d1d5db" }}
                      >
                        {selectedAddressIndex === i && (
                          <div className="w-2.5 h-2.5 rounded-full" style={{ background: "#00BFB3" }} />
                        )}
                      </div>
                    </div>
                  </button>
                ))}
                <button
                  onClick={() => navigate("/profile")}
                  className="w-full py-3 rounded-2xl border-2 border-dashed border-gray-200 flex items-center justify-center gap-2 text-sm font-medium text-gray-500 hover:border-[#00BFB3] hover:text-[#00BFB3] transition-colors bg-white"
                >
                  <Plus size={16} /> Thêm địa chỉ mới
                </button>
              </div>
            )}

            {step === "shipping" && (
              <div>
                <h2 className="font-bold text-gray-800 text-lg mb-4">Chọn phương thức vận chuyển</h2>
                <div className="space-y-3">
                  {shippingOptions.map((method) => (
                    <button
                      key={method.id}
                      onClick={() => setShippingChoice(method.id)}
                      className="w-full p-4 rounded-2xl border-2 text-left transition-all bg-white"
                      style={{
                        borderColor: selectedShippingId === method.id ? "#00BFB3" : "#e5e7eb",
                        background: selectedShippingId === method.id ? "rgba(0,191,179,0.04)" : "white",
                      }}
                    >
                      <div className="flex items-center justify-between">
                        <div className="flex items-center gap-3">
                          <Truck
                            size={20}
                            style={{ color: selectedShippingId === method.id ? "#00BFB3" : "#6b7280" }}
                          />
                          <div>
                            <p className="font-semibold text-sm text-gray-800">{method.name}</p>
                            <p className="text-xs text-gray-500">
                              {method.desc} · Dự kiến: {method.eta}
                            </p>
                          </div>
                        </div>
                        <div className="text-right">
                          <p
                            className="font-bold text-sm"
                            style={{ color: method.fee === 0 ? "#00BFB3" : "#374151" }}
                          >
                            {method.fee === 0 ? "Miễn phí" : formatPrice(method.fee)}
                          </p>
                        </div>
                      </div>
                    </button>
                  ))}
                </div>
                <div className="mt-5">
                  <label className="block text-sm font-semibold text-gray-700 mb-2">
                    Ghi chú cho người bán (tùy chọn)
                  </label>
                  <textarea
                    value={note}
                    onChange={(e) => setNote(e.target.value)}
                    rows={3}
                    placeholder="VD: Giao giờ hành chính, gọi trước khi giao..."
                    className="w-full px-4 py-3 border border-gray-200 rounded-xl text-sm outline-none focus:border-[#00BFB3] resize-none bg-white"
                  />
                </div>
              </div>
            )}

            {step === "payment" && (
              <div>
                <h2 className="font-bold text-gray-800 text-lg mb-4">Phương thức thanh toán</h2>
                <div className="space-y-3">
                  {paymentOptions.map((method) => (
                    <button
                      key={method.id}
                      onClick={() => setSelectedPaymentId(method.id)}
                      className="w-full p-4 rounded-2xl border-2 text-left transition-all bg-white"
                      style={{
                        borderColor: selectedPaymentId === method.id ? "#00BFB3" : "#e5e7eb",
                        background:
                          selectedPaymentId === method.id ? "rgba(0,191,179,0.04)" : "white",
                      }}
                    >
                      <div className="flex items-center gap-4">
                        <span className="text-2xl">{method.icon}</span>
                        <div className="flex-1">
                          <p className="font-semibold text-sm text-gray-800">{method.name}</p>
                          <p className="text-xs text-gray-500">{method.desc}</p>
                        </div>
                        <div
                          className="w-5 h-5 rounded-full border-2 flex items-center justify-center"
                          style={{
                            borderColor: selectedPaymentId === method.id ? "#00BFB3" : "#d1d5db",
                          }}
                        >
                          {selectedPaymentId === method.id && (
                            <div
                              className="w-2.5 h-2.5 rounded-full"
                              style={{ background: "#00BFB3" }}
                            />
                          )}
                        </div>
                      </div>
                    </button>
                  ))}
                </div>
                <div className="mt-4 flex items-center gap-2 text-xs text-gray-500 px-1">
                  <Shield size={14} style={{ color: "#00BFB3" }} />
                  <span>Tất cả giao dịch được mã hóa SSL 256-bit và bảo mật tuyệt đối</span>
                </div>
              </div>
            )}

            {step === "review" && (
              <div className="space-y-4">
                <h2 className="font-bold text-gray-800 text-lg mb-4">Xác nhận đơn hàng</h2>

                <div className="bg-white rounded-2xl p-4 shadow-sm">
                  <div className="flex items-center justify-between mb-3">
                    <h3 className="font-semibold text-gray-700 flex items-center gap-2">
                      <MapPin size={15} style={{ color: "#00BFB3" }} /> Địa chỉ giao hàng
                    </h3>
                    <button
                      onClick={() => setStep("address")}
                      className="text-xs"
                      style={{ color: "#00BFB3" }}
                    >
                      Thay đổi
                    </button>
                  </div>
                  {addresses[selectedAddressIndex] ? (
                    <div className="text-sm">
                      <p className="font-semibold text-gray-800">
                        {buyerName}
                        {addresses[selectedAddressIndex].phone && (
                          <> · {addresses[selectedAddressIndex].phone}</>
                        )}
                      </p>
                      <p className="text-gray-500 mt-0.5">
                        {formatAddressLine(addresses[selectedAddressIndex])}
                      </p>
                    </div>
                  ) : (
                    <p className="text-sm text-red-500">Chưa có địa chỉ giao hàng</p>
                  )}
                </div>

                <div className="bg-white rounded-2xl p-4 shadow-sm">
                  <div className="flex items-center justify-between mb-2">
                    <h3 className="font-semibold text-gray-700 flex items-center gap-2">
                      <Truck size={15} style={{ color: "#00BFB3" }} /> Vận chuyển
                    </h3>
                    <button
                      onClick={() => setStep("shipping")}
                      className="text-xs"
                      style={{ color: "#00BFB3" }}
                    >
                      Thay đổi
                    </button>
                  </div>
                  <p className="text-sm font-medium text-gray-700">{shipping?.name}</p>
                  <p className="text-xs text-gray-500">Dự kiến: {shipping?.eta}</p>
                </div>

                <div className="bg-white rounded-2xl p-4 shadow-sm">
                  <div className="flex items-center justify-between mb-2">
                    <h3 className="font-semibold text-gray-700 flex items-center gap-2">
                      <CreditCard size={15} style={{ color: "#00BFB3" }} /> Thanh toán
                    </h3>
                    <button
                      onClick={() => setStep("payment")}
                      className="text-xs"
                      style={{ color: "#00BFB3" }}
                    >
                      Thay đổi
                    </button>
                  </div>
                  <p className="text-sm font-medium text-gray-700">
                    {paymentOptions.find((p) => p.id === selectedPaymentId)?.name}
                  </p>
                </div>

                <div className="bg-white rounded-2xl p-4 shadow-sm">
                  <h3 className="font-semibold text-gray-700 flex items-center gap-2 mb-3">
                    <Package size={15} style={{ color: "#00BFB3" }} /> Sản phẩm ({cartItems.length})
                  </h3>
                  <div className="space-y-3">
                    {cartItems.map((item) => (
                      <div key={item.productId} className="flex items-center gap-3">
                        {item.image ? (
                          <img
                            src={item.image}
                            alt={item.name ?? ""}
                            className="w-12 h-12 rounded-lg object-cover"
                            loading="lazy"
                          />
                        ) : (
                          <div className="w-12 h-12 rounded-lg bg-gray-100" />
                        )}
                        <div className="flex-1 min-w-0">
                          <p className="text-sm font-medium text-gray-800 truncate">
                            {item.name ?? item.productId}
                          </p>
                          <p className="text-xs text-gray-400">x{item.quantity}</p>
                        </div>
                        <span className="text-sm font-bold" style={{ color: "#FF6200" }}>
                          {formatPrice(item.price * item.quantity)}
                        </span>
                      </div>
                    ))}
                  </div>
                </div>
              </div>
            )}
          </motion.div>
        </AnimatePresence>

        <div className="lg:sticky lg:top-6 h-fit space-y-4">
          <div className="bg-white rounded-2xl shadow-sm p-5">
            <h3 className="font-bold text-gray-800 mb-4">Đơn hàng của bạn</h3>
            <div className="space-y-2 text-sm mb-4">
              {cartItems.slice(0, 3).map((item) => (
                <div key={item.productId} className="flex justify-between gap-2">
                  <span className="text-gray-600 truncate">
                    {(item.name ?? item.productId).split(" ").slice(0, 4).join(" ")}... x{item.quantity}
                  </span>
                  <span className="font-medium shrink-0">
                    {formatPrice(item.price * item.quantity)}
                  </span>
                </div>
              ))}
              {cartItems.length > 3 && (
                <p className="text-xs text-gray-400">+{cartItems.length - 3} sản phẩm khác</p>
              )}
            </div>
            <div className="border-t border-gray-100 pt-3 space-y-2">
              <div className="flex justify-between text-sm">
                <span className="text-gray-500">Tạm tính</span>
                <span>{formatPrice(subtotal)}</span>
              </div>
              <div className="flex justify-between text-sm">
                <span className="text-gray-500">Phí vận chuyển</span>
                <span style={{ color: shippingFee === 0 ? "#00BFB3" : "#374151" }}>
                  {shippingFee === 0 ? "Miễn phí" : formatPrice(shippingFee)}
                </span>
              </div>
              {discount > 0 && (
                <div className="flex justify-between text-sm">
                  <span className="text-gray-500">Giảm giá</span>
                  <span style={{ color: "#00BFB3" }}>-{formatPrice(discount)}</span>
                </div>
              )}
              <div className="flex justify-between font-black text-base pt-2 border-t border-gray-100">
                <span>Tổng cộng</span>
                <span style={{ color: "#FF6200" }}>{formatPrice(finalTotal)}</span>
              </div>
            </div>

            <button
              onClick={handleNext}
              disabled={isProcessing || (step === "address" && addresses.length === 0)}
              className="w-full mt-5 py-3.5 rounded-xl text-white font-bold text-sm flex items-center justify-center gap-2 shadow-lg hover:opacity-90 transition-all disabled:opacity-50 disabled:cursor-not-allowed"
              style={{ background: "linear-gradient(135deg, #FF6200, #ff8a40)" }}
            >
              {isProcessing ? (
                <span className="flex items-center gap-2">
                  <span className="w-4 h-4 rounded-full border-2 border-white/30 border-t-white animate-spin" />
                  Đang xử lý...
                </span>
              ) : step === "review" ? (
                <>
                  <CheckCircle size={16} /> Đặt hàng ngay
                </>
              ) : (
                <>
                  Tiếp tục <ChevronRight size={16} />
                </>
              )}
            </button>

            {step === "review" && (
              <p className="text-[11px] text-gray-400 mt-3 text-center">
                Bằng cách đặt hàng, bạn đồng ý với chính sách của VNShop.
              </p>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
