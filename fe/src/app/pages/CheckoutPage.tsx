import { useQuery, useMutation } from "@tanstack/react-query";
import {
  MapPin,
  Truck,
  CreditCard,
  CheckCircle,
  ChevronRight,
  ArrowLeft,
  Plus,
  Shield,
  Package,
  AlertCircle,
  LogIn,
  Check,
  Tag,
} from "lucide-react";
import { motion, AnimatePresence } from "motion/react";
import { useMemo, useRef, useState } from "react";
import { Trans, useTranslation } from "react-i18next";
import { useNavigate } from "react-router";
import { toast } from "sonner";
import { v4 as uuidv4 } from "uuid";

import { useAuth } from "../hooks/use-auth";
import { useCart } from "../hooks/use-cart";
import {
  calculateCheckout,
  paymentMethods as fetchPaymentMethods,
  shippingOptions as fetchShippingOptions,
} from "../lib/api/endpoints/checkout";
import { listActiveCoupons, validateCouponCode } from "../lib/api/endpoints/coupons";
import { placeOrder } from "../lib/api/endpoints/orders";
import { codConfirm, momoCreate, vnpayCreate } from "../lib/api/endpoints/payment";
import { myProfile } from "../lib/api/endpoints/users";
import { ApiError } from "../lib/api/envelope";
import { formatPrice } from "../lib/format";
import type { Address } from "../types/api";

type Step = "address" | "shipping" | "payment" | "review" | "success";

const STEPS: { id: Step; labelKey: string; icon: typeof MapPin }[] = [
  { id: "address", labelKey: "checkout.steps.address", icon: MapPin },
  { id: "shipping", labelKey: "checkout.steps.shipping", icon: Truck },
  { id: "payment", labelKey: "checkout.steps.payment", icon: CreditCard },
  { id: "review", labelKey: "checkout.steps.review", icon: CheckCircle },
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
  {
    id: "standard",
    name: "Giao Hàng Tiêu Chuẩn",
    desc: "Giao trong 1-2 ngày",
    fee: 30000,
    eta: "Ngày mai",
  },
  {
    id: "economy",
    name: "Giao Hàng Tiết Kiệm",
    desc: "Giao trong 3-5 ngày",
    fee: 20000,
    eta: "3-5 ngày",
  },
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
  const { t } = useTranslation();

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
      name: s.name ?? t("checkout.shipping.fallbackName", { n: i + 1 }),
      desc:
        typeof s.estimatedDays === "number"
          ? t("checkout.shipping.deliverInDays", { n: s.estimatedDays })
          : t("checkout.shipping.etaStandard"),
      fee: s.fee ?? 0,
      eta:
        typeof s.estimatedDays === "number"
          ? t("checkout.shipping.etaDays", { n: s.estimatedDays })
          : t("checkout.shipping.etaStandard"),
    }));
  }, [shippingQuery.data, t]);

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
      .map(
        (p) =>
          codeToFallback[p.code] ?? {
            id: p.code as PaymentOption["id"],
            name: p.name,
            icon: "💳",
            desc: p.description ?? "",
          },
      );
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
  const [couponInput, setCouponInput] = useState<string>("");
  const [appliedCoupon, setAppliedCoupon] = useState<string | null>(null);
  const [showCouponPicker, setShowCouponPicker] = useState(false);
  const [isProcessing, setIsProcessing] = useState(false);
  const [placedOrderId, setPlacedOrderId] = useState<string | null>(null);

  // Lazily fetch the public coupon catalogue when the user opens the picker.
  // No `enabled` gate would mean every checkout view paid the call even if
  // the user typed a code by hand or skipped coupons entirely.
  const couponsQuery = useQuery({
    queryKey: ["checkout", "active-coupons"],
    queryFn: listActiveCoupons,
    enabled: showCouponPicker,
    staleTime: 60_000,
  });

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
      appliedCoupon,
    ],
    queryFn: () =>
      calculateCheckout({
        items: cartItems.map((i) => ({ productId: i.productId, quantity: i.quantity })),
        couponCode: appliedCoupon ?? undefined,
      }),
    enabled: cartItems.length > 0,
    retry: false,
  });

  const shipping = shippingOptions.find((m) => m.id === selectedShippingId) ?? shippingOptions[0];
  // Shipping fee is computed client-side from the selected option. The BE
  // /checkout/calculate endpoint does not currently accept shipping choices, so
  // its `shippingFee` reflects a single default. Using the client-selected fee
  // keeps the total in sync when the user toggles shipping options. We still
  // pull `subtotal` and `discount` from the BE so server-authoritative coupon
  // pricing wins.
  const subtotal = calcQuery.data?.subtotal ?? totalAmount;
  const shippingFee = shipping?.fee ?? 0;
  const discount = calcQuery.data?.discount ?? 0;
  const finalTotal = Math.max(0, subtotal - discount) + shippingFee;

  const stepOrder: Step[] = ["address", "shipping", "payment", "review", "success"];
  const stepIdx = stepOrder.indexOf(step);

  const buyerName =
    [profile?.firstName, profile?.lastName].filter(Boolean).join(" ") ||
    profile?.username ||
    profile?.email ||
    t("checkout.buyerFallback");

  const couponMutation = useMutation({
    mutationFn: validateCouponCode,
    onSuccess: (result, variables) => {
      if (result.valid) {
        setAppliedCoupon(variables.code);
        setCouponInput("");
        return;
      }
      toast.error(result.message || t("checkout.summary.couponInvalid"));
    },
    onError: (err) => {
      toast.error(err instanceof ApiError ? err.message : t("checkout.summary.couponInvalid"));
    },
  });

  const handleApplyCoupon = () => {
    const code = couponInput.trim();
    if (!code || couponMutation.isPending) return;
    couponMutation.mutate({ code, orderAmount: subtotal });
  };

  const handlePickCoupon = (code: string) => {
    setCouponInput(code);
    setShowCouponPicker(false);
    if (!couponMutation.isPending) {
      couponMutation.mutate({ code, orderAmount: subtotal });
    }
  };

  const handleRemoveCoupon = () => {
    setAppliedCoupon(null);
    setCouponInput("");
    couponMutation.reset();
  };

  if (!ready) {
    return (
      <div className="max-w-3xl mx-auto px-4 py-24 text-center text-sm text-gray-500">
        {t("checkout.initSession")}
      </div>
    );
  }

  if (!authenticated) {
    return (
      <div className="max-w-3xl mx-auto px-4 py-24 text-center">
        <h2 className="text-xl font-bold text-gray-600 mb-3">{t("checkout.loginPromptTitle")}</h2>
        <button
          onClick={() => login("/checkout")}
          className="px-8 py-3 rounded-xl text-white font-semibold inline-flex items-center gap-2"
          style={{ background: "linear-gradient(135deg, #00BFB3, #009990)" }}
        >
          <LogIn size={16} /> {t("auth.login")}
        </button>
      </div>
    );
  }

  if (cartLoading) {
    return (
      <div className="max-w-3xl mx-auto px-4 py-24 text-center text-sm text-gray-500">
        {t("checkout.loadingCart")}
      </div>
    );
  }

  if (cartItems.length === 0 && step !== "success") {
    return (
      <div className="max-w-3xl mx-auto px-4 py-24 text-center">
        <Package size={56} className="mx-auto mb-4 text-gray-200" />
        <h2 className="text-xl font-bold text-gray-600 mb-3">{t("checkout.emptyCartTitle")}</h2>
        <button
          onClick={() => navigate("/")}
          className="px-6 py-2.5 rounded-xl text-white font-medium"
          style={{ background: "#00BFB3" }}
        >
          {t("checkout.continueShopping")}
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
          couponCode: appliedCoupon ?? undefined,
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
              ? await vnpayCreate(
                  {
                    orderId: order.id,
                    returnUrl: `${window.location.origin}/payment/return/vnpay`,
                  },
                  idempotencyKeyRef.current,
                )
              : await momoCreate(
                  {
                    orderId: order.id,
                    returnUrl: `${window.location.origin}/payment/return/momo`,
                  },
                  idempotencyKeyRef.current,
                );
          window.location.href = init.redirectUrl;
          return;
        } catch (err) {
          toast.error(
            err instanceof ApiError
              ? t("checkout.payment.initFailedPrefix", { message: err.message })
              : t("checkout.payment.initFailedShort"),
          );
          // Fall through to success screen — order exists; buyer can pay later.
        }
      } else if (selectedPaymentId === "COD") {
        try {
          await codConfirm({ orderId: order.id }, idempotencyKeyRef.current);
        } catch {
          // COD confirmation is best-effort; buyer will see status update later.
        }
      }

      setStep("success");
    } catch (err) {
      const message =
        err instanceof ApiError ? err.message : t("checkout.payment.placeOrderFailed");
      toast.error(message, {
        description:
          err instanceof ApiError && err.correlationId
            ? t("checkout.payment.supportCode", { id: err.correlationId })
            : undefined,
      });
    } finally {
      setIsProcessing(false);
    }
  };

  const handleNext = () => {
    if (step === "address") {
      if (addresses.length === 0) {
        toast.error(t("checkout.address.missingValidation"));
        void navigate("/profile");
        return;
      }
      setStep("shipping");
      return;
    }
    if (step === "shipping") {
      if (!selectedShippingId) {
        toast.error(t("checkout.shipping.missingValidation"));
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
            {t("checkout.success.title")}
          </h1>
          {placedOrderId ? (
            <>
              <p className="text-gray-500 mb-2">{t("checkout.success.orderIdLabel")}</p>
              <div
                className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl mb-6 font-mono font-bold text-lg"
                style={{ background: "rgba(0,191,179,0.08)", color: "#00BFB3" }}
              >
                {placedOrderId}
              </div>
            </>
          ) : null}
          {selectedPaymentId === "COD" ? (
            <p className="text-sm text-gray-500 mb-2">
              <Trans
                i18nKey="checkout.success.codNotice"
                values={{ amount: formatPrice(finalTotal) }}
                components={{ 1: <strong /> }}
              />
            </p>
          ) : null}
          <div className="flex gap-3">
            <button
              onClick={() => navigate("/orders")}
              className="flex-1 py-3 rounded-xl border-2 font-semibold text-sm transition-colors"
              style={{ borderColor: "#00BFB3", color: "#00BFB3" }}
            >
              {t("checkout.success.viewOrders")}
            </button>
            <button
              onClick={() => navigate("/")}
              className="flex-1 py-3 rounded-xl text-white font-semibold text-sm"
              style={{ background: "linear-gradient(135deg, #00BFB3, #009990)" }}
            >
              {t("checkout.success.continueShopping")}
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
        <h1 className="text-xl font-bold text-gray-800">{t("checkout.title")}</h1>
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
                  {t(s.labelKey)}
                </span>
              </div>
              {i < STEPS.length - 1 ? (
                <div
                  className="w-16 sm:w-24 h-0.5 mb-5 mx-1 transition-colors"
                  style={{ background: isDone ? "#00BFB3" : "#e5e7eb" }}
                />
              ) : null}
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
            {step === "address" ? (
              <div className="space-y-4">
                <h2 className="font-bold text-gray-800 text-lg mb-4">
                  {t("checkout.address.header")}
                </h2>
                {profileQuery.isLoading ? (
                  <p className="text-sm text-gray-400">{t("checkout.address.loading")}</p>
                ) : null}
                {!profileQuery.isLoading && addresses.length === 0 ? (
                  <div className="bg-white rounded-2xl p-5 text-sm text-gray-500 flex items-start gap-3">
                    <AlertCircle size={18} className="text-orange-500 shrink-0 mt-0.5" />
                    <div>
                      <p className="font-semibold text-gray-700">
                        {t("checkout.address.noAddressesTitle")}
                      </p>
                      <p className="mt-1">{t("checkout.address.noAddressesSub")}</p>
                      <button
                        onClick={() => navigate("/profile")}
                        className="mt-3 text-sm font-semibold"
                        style={{ color: "#00BFB3" }}
                      >
                        {t("checkout.address.goToProfile")}
                      </button>
                    </div>
                  </div>
                ) : null}
                {addresses.map((addr, i) => (
                  <button
                    // eslint-disable-next-line react/no-array-index-key -- address list has no stable id; index is the address position
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
                          {addr.phone ? (
                            <>
                              <span className="text-gray-400 text-sm">|</span>
                              <span className="text-gray-600 text-sm">{addr.phone}</span>
                            </>
                          ) : null}
                          {addr.isDefault ? (
                            <span
                              className="px-1.5 py-0.5 rounded text-xs font-medium border"
                              style={{ borderColor: "#FF6200", color: "#FF6200" }}
                            >
                              {t("checkout.address.isDefaultBadge")}
                            </span>
                          ) : null}
                        </div>
                        <p className="text-sm text-gray-500">{formatAddressLine(addr)}</p>
                      </div>
                      <div
                        className="w-5 h-5 rounded-full border-2 flex items-center justify-center shrink-0 mt-1 transition-all"
                        style={{ borderColor: selectedAddressIndex === i ? "#00BFB3" : "#d1d5db" }}
                      >
                        {selectedAddressIndex === i ? (
                          <div
                            className="w-2.5 h-2.5 rounded-full"
                            style={{ background: "#00BFB3" }}
                          />
                        ) : null}
                      </div>
                    </div>
                  </button>
                ))}
                <button
                  onClick={() => navigate("/profile")}
                  className="w-full py-3 rounded-2xl border-2 border-dashed border-gray-200 flex items-center justify-center gap-2 text-sm font-medium text-gray-500 hover:border-[#00BFB3] hover:text-[#00BFB3] transition-colors bg-white"
                >
                  <Plus size={16} /> {t("checkout.address.addNew")}
                </button>
              </div>
            ) : null}

            {step === "shipping" ? (
              <div>
                <h2 className="font-bold text-gray-800 text-lg mb-4">
                  {t("checkout.shipping.header")}
                </h2>
                <div className="space-y-3">
                  {shippingOptions.map((method) => (
                    <button
                      key={method.id}
                      onClick={() => setShippingChoice(method.id)}
                      className="w-full p-4 rounded-2xl border-2 text-left transition-all bg-white"
                      style={{
                        borderColor: selectedShippingId === method.id ? "#00BFB3" : "#e5e7eb",
                        background:
                          selectedShippingId === method.id ? "rgba(0,191,179,0.04)" : "white",
                      }}
                    >
                      <div className="flex items-center justify-between">
                        <div className="flex items-center gap-3">
                          <Truck
                            size={20}
                            style={{
                              color: selectedShippingId === method.id ? "#00BFB3" : "#6b7280",
                            }}
                          />
                          <div>
                            <p className="font-semibold text-sm text-gray-800">{method.name}</p>
                            <p className="text-xs text-gray-500">
                              {method.desc} ·{" "}
                              {t("checkout.shipping.etaPrefix", { eta: method.eta })}
                            </p>
                          </div>
                        </div>
                        <div className="text-right">
                          <p
                            className="font-bold text-sm"
                            style={{ color: method.fee === 0 ? "#00BFB3" : "#374151" }}
                          >
                            {method.fee === 0
                              ? t("checkout.shipping.free")
                              : formatPrice(method.fee)}
                          </p>
                        </div>
                      </div>
                    </button>
                  ))}
                </div>
                <div className="mt-5">
                  <label
                    htmlFor="checkout-note"
                    className="block text-sm font-semibold text-gray-700 mb-2"
                  >
                    {t("checkout.shipping.noteLabel")}
                  </label>
                  <textarea
                    id="checkout-note"
                    value={note}
                    onChange={(e) => setNote(e.target.value)}
                    rows={3}
                    placeholder={t("checkout.shipping.notePlaceholder")}
                    className="w-full px-4 py-3 border border-gray-200 rounded-xl text-sm outline-none focus:border-[#00BFB3] resize-none bg-white"
                  />
                </div>
              </div>
            ) : null}

            {step === "payment" ? (
              <div>
                <h2 className="font-bold text-gray-800 text-lg mb-4">
                  {t("checkout.payment.header")}
                </h2>
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
                          {selectedPaymentId === method.id ? (
                            <div
                              className="w-2.5 h-2.5 rounded-full"
                              style={{ background: "#00BFB3" }}
                            />
                          ) : null}
                        </div>
                      </div>
                    </button>
                  ))}
                </div>
                <div className="mt-4 flex items-center gap-2 text-xs text-gray-500 px-1">
                  <Shield size={14} style={{ color: "#00BFB3" }} />
                  <span>{t("checkout.payment.sslNotice")}</span>
                </div>
              </div>
            ) : null}

            {step === "review" ? (
              <div className="space-y-4">
                <h2 className="font-bold text-gray-800 text-lg mb-4">
                  {t("checkout.review.header")}
                </h2>

                <div className="bg-white rounded-2xl p-4 shadow-sm">
                  <div className="flex items-center justify-between mb-3">
                    <h3 className="font-semibold text-gray-700 flex items-center gap-2">
                      <MapPin size={15} style={{ color: "#00BFB3" }} />{" "}
                      {t("checkout.review.shippingAddress")}
                    </h3>
                    <button
                      onClick={() => setStep("address")}
                      className="text-xs"
                      style={{ color: "#00BFB3" }}
                    >
                      {t("checkout.review.change")}
                    </button>
                  </div>
                  {addresses[selectedAddressIndex] ? (
                    <div className="text-sm">
                      <p className="font-semibold text-gray-800">
                        {buyerName}
                        {addresses[selectedAddressIndex].phone ? (
                          <> · {addresses[selectedAddressIndex].phone}</>
                        ) : null}
                      </p>
                      <p className="text-gray-500 mt-0.5">
                        {formatAddressLine(addresses[selectedAddressIndex])}
                      </p>
                    </div>
                  ) : (
                    <p className="text-sm text-red-500">{t("checkout.review.noAddress")}</p>
                  )}
                </div>

                <div className="bg-white rounded-2xl p-4 shadow-sm">
                  <div className="flex items-center justify-between mb-2">
                    <h3 className="font-semibold text-gray-700 flex items-center gap-2">
                      <Truck size={15} style={{ color: "#00BFB3" }} />{" "}
                      {t("checkout.review.shippingMethod")}
                    </h3>
                    <button
                      onClick={() => setStep("shipping")}
                      className="text-xs"
                      style={{ color: "#00BFB3" }}
                    >
                      {t("checkout.review.change")}
                    </button>
                  </div>
                  <p className="text-sm font-medium text-gray-700">{shipping?.name}</p>
                  <p className="text-xs text-gray-500">
                    {t("checkout.shipping.etaPrefix", { eta: shipping?.eta ?? "" })}
                  </p>
                </div>

                <div className="bg-white rounded-2xl p-4 shadow-sm">
                  <div className="flex items-center justify-between mb-2">
                    <h3 className="font-semibold text-gray-700 flex items-center gap-2">
                      <CreditCard size={15} style={{ color: "#00BFB3" }} />{" "}
                      {t("checkout.review.paymentMethod")}
                    </h3>
                    <button
                      onClick={() => setStep("payment")}
                      className="text-xs"
                      style={{ color: "#00BFB3" }}
                    >
                      {t("checkout.review.change")}
                    </button>
                  </div>
                  <p className="text-sm font-medium text-gray-700">
                    {paymentOptions.find((p) => p.id === selectedPaymentId)?.name}
                  </p>
                </div>

                <div className="bg-white rounded-2xl p-4 shadow-sm">
                  <h3 className="font-semibold text-gray-700 flex items-center gap-2 mb-3">
                    <Package size={15} style={{ color: "#00BFB3" }} />{" "}
                    {t("checkout.review.productsCount", { count: cartItems.length })}
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
            ) : null}
          </motion.div>
        </AnimatePresence>

        <div className="lg:sticky lg:top-6 h-fit space-y-4">
          <div className="bg-white rounded-2xl shadow-sm p-5">
            <h3 className="font-bold text-gray-800 mb-4">{t("checkout.summary.title")}</h3>
            <div className="space-y-2 text-sm mb-4">
              {cartItems.slice(0, 3).map((item) => (
                <div key={item.productId} className="flex justify-between gap-2">
                  <span className="text-gray-600 truncate">
                    {(item.name ?? item.productId).split(" ").slice(0, 4).join(" ")}... x
                    {item.quantity}
                  </span>
                  <span className="font-medium shrink-0">
                    {formatPrice(item.price * item.quantity)}
                  </span>
                </div>
              ))}
              {cartItems.length > 3 ? (
                <p className="text-xs text-gray-400">
                  {t("checkout.summary.moreItems", { count: cartItems.length - 3 })}
                </p>
              ) : null}
            </div>
            <div className="mb-4">
              <div className="flex items-center justify-between mb-2">
                <label
                  htmlFor="checkout-coupon-input"
                  className="block text-xs font-semibold text-gray-600"
                >
                  {t("checkout.summary.couponLabel")}
                </label>
                {!appliedCoupon ? (
                  <button
                    onClick={() => setShowCouponPicker((v) => !v)}
                    className="flex items-center gap-1 text-xs font-semibold transition-colors"
                    style={{ color: "#00BFB3" }}
                    type="button"
                  >
                    <Tag size={12} />
                    {showCouponPicker
                      ? t("checkout.summary.togglePickerHide")
                      : t("checkout.summary.togglePicker")}
                  </button>
                ) : null}
              </div>
              <AnimatePresence>
                {showCouponPicker && !appliedCoupon ? (
                  <motion.div
                    key="picker"
                    initial={{ opacity: 0, height: 0 }}
                    animate={{ opacity: 1, height: "auto" }}
                    exit={{ opacity: 0, height: 0 }}
                    transition={{ duration: 0.2 }}
                    className="overflow-hidden mb-3"
                  >
                    <div className="rounded-lg border border-gray-200 bg-gray-50/50 p-2 max-h-48 overflow-y-auto">
                      {couponsQuery.isLoading ? (
                        <p className="text-xs text-gray-500 px-2 py-1">
                          {t("checkout.summary.couponLoading")}
                        </p>
                      ) : null}
                      {couponsQuery.error instanceof ApiError ? (
                        <p className="text-xs text-red-500 px-2 py-1">
                          {t("checkout.summary.couponLoadFail", {
                            message: couponsQuery.error.message,
                          })}
                        </p>
                      ) : null}
                      {couponsQuery.data?.length === 0 ? (
                        <p className="text-xs text-gray-500 px-2 py-2 text-center">
                          {t("checkout.summary.couponEmpty")}
                        </p>
                      ) : null}
                      {couponsQuery.data && couponsQuery.data.length > 0 ? (
                        <ul className="space-y-1">
                          {couponsQuery.data.map((coupon) => {
                            const value = coupon.value ?? coupon.discountValue;
                            const type = (
                              coupon.type ??
                              coupon.discountType ??
                              "FIXED"
                            ).toUpperCase();
                            const label =
                              type === "PERCENT" && value !== undefined
                                ? t("checkout.summary.couponDiscountPct", { value })
                                : value !== undefined
                                  ? t("checkout.summary.couponDiscountAmount", {
                                      value: formatPrice(value),
                                    })
                                  : t("checkout.summary.couponDiscountFallback");
                            const minLabel = coupon.minOrderValue
                              ? t("checkout.summary.couponMinOrder", {
                                  value: formatPrice(coupon.minOrderValue),
                                })
                              : null;
                            const eligible =
                              !coupon.minOrderValue || subtotal >= coupon.minOrderValue;
                            return (
                              <li key={coupon.code}>
                                <button
                                  onClick={() => handlePickCoupon(coupon.code)}
                                  disabled={!eligible}
                                  className="w-full flex items-center justify-between gap-2 px-3 py-2 text-left rounded-lg bg-white border border-gray-200 hover:border-[#00BFB3] hover:shadow-sm transition-all disabled:opacity-50 disabled:cursor-not-allowed disabled:hover:border-gray-200 disabled:hover:shadow-none"
                                  type="button"
                                >
                                  <div className="min-w-0">
                                    <p
                                      className="text-sm font-bold font-mono truncate"
                                      style={{ color: "#00BFB3" }}
                                    >
                                      {coupon.code}
                                    </p>
                                    <p className="text-xs text-gray-500 truncate">
                                      {label}
                                      {minLabel ? ` · ${minLabel}` : ""}
                                    </p>
                                  </div>
                                  <span
                                    className="text-xs font-semibold shrink-0"
                                    style={{ color: eligible ? "#FF6200" : "#9ca3af" }}
                                  >
                                    {eligible
                                      ? t("checkout.summary.couponApply")
                                      : t("checkout.summary.couponInsufficient")}
                                  </span>
                                </button>
                              </li>
                            );
                          })}
                        </ul>
                      ) : null}
                    </div>
                  </motion.div>
                ) : null}
              </AnimatePresence>
              <AnimatePresence mode="wait">
                {!appliedCoupon ? (
                  <motion.div
                    key="input"
                    initial={{ opacity: 0, y: -4 }}
                    animate={{ opacity: 1, y: 0 }}
                    exit={{ opacity: 0, y: -4 }}
                    transition={{ duration: 0.18 }}
                    className="flex gap-2"
                  >
                    <input
                      id="checkout-coupon-input"
                      type="text"
                      value={couponInput}
                      onChange={(e) => setCouponInput(e.target.value)}
                      onKeyDown={(e) => e.key === "Enter" && handleApplyCoupon()}
                      placeholder={t("checkout.summary.couponPlaceholder")}
                      className="flex-1 px-3 py-2 text-sm border border-gray-200 rounded-lg outline-none focus:border-[#00BFB3] bg-white transition-colors"
                    />
                    <button
                      onClick={handleApplyCoupon}
                      disabled={!couponInput.trim() || couponMutation.isPending}
                      className="px-4 py-2 text-sm font-semibold text-white rounded-lg transition-all disabled:opacity-50 disabled:cursor-not-allowed hover:opacity-90"
                      style={{ background: "#00BFB3" }}
                    >
                      {t("checkout.summary.couponApply")}
                    </button>
                  </motion.div>
                ) : (
                  <motion.div
                    key="applied"
                    initial={{ opacity: 0, y: -4 }}
                    animate={{ opacity: 1, y: 0 }}
                    exit={{ opacity: 0, y: -4 }}
                    transition={{ duration: 0.18 }}
                    className="flex items-center justify-between"
                  >
                    <div
                      className="flex items-center gap-2 px-3 py-2 rounded-lg text-sm font-medium"
                      style={{ background: "rgba(0,191,179,0.08)", color: "#00BFB3" }}
                    >
                      <Check size={14} />
                      <span>{t("checkout.summary.couponApplied", { code: appliedCoupon })}</span>
                      {discount > 0 ? <span className="ml-1">-{formatPrice(discount)}</span> : null}
                    </div>
                    <button
                      onClick={handleRemoveCoupon}
                      className="px-3 py-2 text-sm font-semibold text-gray-600 hover:text-gray-800 transition-colors"
                    >
                      {t("checkout.summary.couponRemove")}
                    </button>
                  </motion.div>
                )}
              </AnimatePresence>
            </div>

            <div className="border-t border-gray-100 pt-3 space-y-2">
              <div className="flex justify-between text-sm">
                <span className="text-gray-500">{t("checkout.summary.subtotal")}</span>
                <span>{formatPrice(subtotal)}</span>
              </div>
              <div className="flex justify-between text-sm">
                <span className="text-gray-500">{t("checkout.summary.shippingFee")}</span>
                <span style={{ color: shippingFee === 0 ? "#00BFB3" : "#374151" }}>
                  {shippingFee === 0 ? t("checkout.summary.free") : formatPrice(shippingFee)}
                </span>
              </div>
              {discount > 0 ? (
                <div className="flex justify-between text-sm">
                  <span className="text-gray-500">{t("checkout.summary.discount")}</span>
                  <span style={{ color: "#FF6200" }}>-{formatPrice(discount)}</span>
                </div>
              ) : null}
              <div className="flex justify-between font-black text-base pt-2 border-t border-gray-100">
                <span>{t("checkout.summary.total")}</span>
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
                  {t("checkout.summary.processing")}
                </span>
              ) : step === "review" ? (
                <>
                  <CheckCircle size={16} /> {t("checkout.summary.placeOrder")}
                </>
              ) : (
                <>
                  {t("checkout.summary.next")} <ChevronRight size={16} />
                </>
              )}
            </button>

            {step === "review" ? (
              <p className="text-[11px] text-gray-400 mt-3 text-center">
                {t("checkout.summary.termsNotice")}
              </p>
            ) : null}
          </div>
        </div>
      </div>
    </div>
  );
}
