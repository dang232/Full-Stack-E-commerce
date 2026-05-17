import { useMutation, useQuery } from "@tanstack/react-query";
import { ArrowLeft, CheckCircle, LogIn, Package } from "lucide-react";
import { AnimatePresence, motion } from "motion/react";
import { useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router";
import { toast } from "sonner";
import { v4 as uuidv4 } from "uuid";

import { useAuth } from "../../hooks/use-auth";
import { useCart } from "../../hooks/use-cart";
import {
  calculateCheckout,
  paymentMethods as fetchPaymentMethods,
  shippingOptions as fetchShippingOptions,
} from "../../lib/api/endpoints/checkout";
import { listActiveCoupons, validateCouponCode } from "../../lib/api/endpoints/coupons";
import { placeOrder } from "../../lib/api/endpoints/orders";
import { codConfirm, momoCreate, vnpayCreate } from "../../lib/api/endpoints/payment";
import { myProfile } from "../../lib/api/endpoints/users";
import { ApiError } from "../../lib/api/envelope";
import type { Address } from "../../types/api";

import { CheckoutAddressStep } from "./CheckoutAddressStep";
import { CheckoutPaymentStep } from "./CheckoutPaymentStep";
import { CheckoutReviewStep } from "./CheckoutReviewStep";
import { CheckoutShippingStep } from "./CheckoutShippingStep";
import { CheckoutSuccess } from "./CheckoutSuccess";
import { CheckoutSummary } from "./CheckoutSummary";
import {
  FALLBACK_PAYMENT,
  FALLBACK_SHIPPING,
  STEPS,
  type PaymentOption,
  type Step,
} from "./types";

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

  const shippingOptions = useMemo(() => {
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
      <CheckoutSuccess
        placedOrderId={placedOrderId}
        selectedPaymentId={selectedPaymentId}
        finalTotal={finalTotal}
      />
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
              <CheckoutAddressStep
                addresses={addresses}
                selectedAddressIndex={selectedAddressIndex}
                setSelectedAddressIndex={setSelectedAddressIndex}
                buyerName={buyerName}
                isLoading={profileQuery.isLoading}
              />
            ) : null}

            {step === "shipping" ? (
              <CheckoutShippingStep
                shippingOptions={shippingOptions}
                selectedShippingId={selectedShippingId}
                setShippingChoice={setShippingChoice}
                note={note}
                setNote={setNote}
              />
            ) : null}

            {step === "payment" ? (
              <CheckoutPaymentStep
                paymentOptions={paymentOptions}
                selectedPaymentId={selectedPaymentId}
                setSelectedPaymentId={setSelectedPaymentId}
              />
            ) : null}

            {step === "review" ? (
              <CheckoutReviewStep
                addresses={addresses}
                selectedAddressIndex={selectedAddressIndex}
                shipping={shipping}
                paymentOptions={paymentOptions}
                selectedPaymentId={selectedPaymentId}
                cartItems={cartItems}
                buyerName={buyerName}
                setStep={setStep}
              />
            ) : null}
          </motion.div>
        </AnimatePresence>

        <CheckoutSummary
          cartItems={cartItems}
          subtotal={subtotal}
          shippingFee={shippingFee}
          discount={discount}
          finalTotal={finalTotal}
          step={step}
          isProcessing={isProcessing}
          addresses={addresses}
          appliedCoupon={appliedCoupon}
          couponInput={couponInput}
          setCouponInput={setCouponInput}
          showCouponPicker={showCouponPicker}
          setShowCouponPicker={setShowCouponPicker}
          couponsQuery={couponsQuery}
          couponMutation={couponMutation}
          handleApplyCoupon={handleApplyCoupon}
          handlePickCoupon={handlePickCoupon}
          handleRemoveCoupon={handleRemoveCoupon}
          handleNext={handleNext}
        />
      </div>
    </div>
  );
}
