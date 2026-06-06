import { ArrowLeft, ChevronRight, LogIn, Minus, Plus, ShieldCheck, ShoppingCart, Tag, Trash2, Truck } from "lucide-react";
import { useMutation } from "@tanstack/react-query";
import { motion, AnimatePresence } from "motion/react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router";
import { toast } from "sonner";

import { ImageWithFallback } from "../components/image-with-fallback";
import { useAuth } from "../hooks/use-auth";
import { useAppConfig } from "../hooks/use-app-config";
import { useCart } from "../hooks/use-cart";
import { ApiError } from "../lib/api";
import { validateCouponCode } from "../lib/api/endpoints/coupons";
import { FREE_SHIPPING_THRESHOLD, FLAT_SHIPPING_FEE } from "../lib/domain-constants";
import { formatPrice } from "../lib/format";
import type { CartItem } from "../types/api";

export function CartPage() {
  const navigate = useNavigate();
  const { ready, authenticated, login } = useAuth();
  const config = useAppConfig();
  const { items, itemCount, totalAmount, isLoading, isHydrating, error, updateItem, removeItem } = useCart();
  const [coupon, setCoupon] = useState("");
  const [appliedCoupon, setAppliedCoupon] = useState<string | null>(null);
  const [couponDiscount, setCouponDiscount] = useState(0);
  const [couponError, setCouponError] = useState("");
  const { t } = useTranslation();

  // The real discount lands at checkout (/checkout/calculate is server-authoritative).
  // For the cart preview we hit /coupons/validate so the buyer sees the same code/discount
  // they will see on the checkout summary.
  const couponMutation = useMutation({
    mutationFn: validateCouponCode,
    onSuccess: (result, variables) => {
      if (result.valid) {
        setAppliedCoupon(variables.code);
        setCouponDiscount(result.discount ?? 0);
        setCouponError("");
        return;
      }
      setAppliedCoupon(null);
      setCouponDiscount(0);
      setCouponError(result.message || t("cart.couponInvalid"));
    },
    onError: (err) => {
      setAppliedCoupon(null);
      setCouponDiscount(0);
      setCouponError(err instanceof ApiError ? err.message : t("cart.couponInvalid"));
    },
  });

  const shippingFee = totalAmount >= FREE_SHIPPING_THRESHOLD ? 0 : FLAT_SHIPPING_FEE;
  const finalTotal = Math.max(0, totalAmount - couponDiscount) + shippingFee;

  const handleApplyCoupon = () => {
    const code = coupon.toUpperCase().trim();
    if (!code || couponMutation.isPending) return;
    couponMutation.mutate({ code, orderAmount: totalAmount });
  };

  const handleRemoveCoupon = () => {
    setAppliedCoupon(null);
    setCouponDiscount(0);
    setCouponError("");
    setCoupon("");
    couponMutation.reset();
  };

  const MAX_ITEM_QTY = 99;

  const onUpdate = (productId: string, quantity: number) => {
    if (quantity <= 0) {
      removeItem(productId, {
        onError: (err) =>
          toast.error(err instanceof ApiError ? err.message : t("cart.errors.cantRemove")),
      });
      return;
    }
    if (quantity > MAX_ITEM_QTY) {
      toast.warning(t("cart.errors.maxQty", { max: MAX_ITEM_QTY }));
      return;
    }
    updateItem(
      { productId, quantity },
      {
        onError: (err) =>
          toast.error(err instanceof ApiError ? err.message : t("cart.errors.cantUpdate")),
      },
    );
  };

  const onRemove = (productId: string) =>
    removeItem(productId, {
      onError: (err) =>
        toast.error(err instanceof ApiError ? err.message : t("cart.errors.cantRemove")),
    });

  if (!ready) {
    return (
      <div className="max-w-7xl mx-auto px-4 py-24 text-center text-sm text-muted-foreground">
        {t("cart.initSession")}
      </div>
    );
  }

  // Guests see their localStorage cart — no login wall here.

  if (isLoading) {
    return (
      <div className="max-w-7xl mx-auto px-4 py-24 text-center text-sm text-muted-foreground">
        {t("cart.loading")}
      </div>
    );
  }

  if (error) {
    const message = error instanceof ApiError ? error.message : t("cart.loadError");
    return (
      <div className="max-w-7xl mx-auto px-4 py-24 text-center">
        <h2 className="text-xl font-semibold text-foreground mb-3">{message}</h2>
        <button
          onClick={() => navigate("/")}
          className="px-6 py-2.5 rounded-xl text-white font-medium"
          style={{ background: "var(--brand-primary)" }}
        >
          {t("cart.backToHome")}
        </button>
      </div>
    );
  }

  if (items.length === 0) {
    return (
      <div className="max-w-7xl mx-auto px-4 py-24 text-center">
        <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }}>
          <ShoppingCart size={80} className="mx-auto mb-6 text-gray-200" />
          <h2 className="text-2xl font-bold text-muted-foreground mb-3">{t("cart.emptyTitle")}</h2>
          <p className="text-muted-foreground mb-8">{t("cart.emptySub")}</p>
          <button
            onClick={() => navigate("/")}
            className="px-8 py-3 rounded-xl text-white font-semibold shadow-lg hover:opacity-90 transition-opacity"
            style={{ background: "linear-gradient(135deg, var(--brand-primary), var(--brand-secondary))" }}
          >
            {t("cart.continueShopping")}
          </button>
        </motion.div>
      </div>
    );
  }

  // Group by seller — sellerId may be missing on legacy items, fall back to a sentinel bucket.
  const grouped = items.reduce<Record<string, { sellerName: string; items: CartItem[] }>>(
    (acc, item) => {
      const sid = item.sellerId ?? "_";
      if (!acc[sid]) {
        acc[sid] = { sellerName: item.sellerId ?? t("cart.sellerFallback"), items: [] };
      }
      acc[sid].items.push(item);
      return acc;
    },
    {},
  );

  return (
    <div className="max-w-7xl mx-auto px-4 py-6">
      <div className="flex items-center gap-3 mb-6">
        <button
          onClick={() => navigate(-1)}
          className="p-2 rounded-xl hover:bg-card transition-colors text-muted-foreground"
        >
          <ArrowLeft size={20} />
        </button>
        <h1
          className="text-2xl font-bold text-foreground"
          
        >
          {t("cart.titleWithCount", { count: itemCount })}
        </h1>
      </div>

      <div className="grid lg:grid-cols-[1fr_380px] gap-6">
        <div className="space-y-4">
          <AnimatePresence>
            {Object.entries(grouped).map(([sellerId, group]) => (
              <motion.div
                key={sellerId}
                initial={{ opacity: 0, y: 12 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, x: -20, height: 0 }}
                className="bg-card rounded-2xl shadow-sm overflow-hidden"
              >
                <div
                  className="flex items-center gap-3 px-5 py-3.5 border-b border-border"
                  style={{ background: "rgba(238,77,45,0.04)" }}
                >
                  <span className="font-semibold text-foreground text-sm">{group.sellerName}</span>
                  <span className="ml-auto text-xs text-muted-foreground flex items-center gap-1">
                    <Truck size={12} />
                    {totalAmount >= FREE_SHIPPING_THRESHOLD
                      ? t("cart.freeShippingTag")
                      : t("cart.shipFee", { fee: formatPrice(FLAT_SHIPPING_FEE) })}
                  </span>
                </div>

                <div className="divide-y divide-gray-50">
                  {group.items.map((item) => (
                    <motion.div
                      key={item.productId}
                      exit={{ opacity: 0, x: -30 }}
                      className="flex gap-4 p-5"
                    >
                      <button
                        type="button"
                        aria-label={`Xem ${item.name ?? "sản phẩm"}`}
                        className="w-20 h-20 rounded-xl overflow-hidden shrink-0 cursor-pointer bg-muted block p-0 border-0"
                        onClick={() => navigate(`/product/${item.productId}`)}
                      >
                        <ImageWithFallback
                          src={item.image ?? ""}
                          alt={item.name ?? ""}
                          className="w-full h-full object-cover"
                        />
                      </button>
                      <div className="flex-1 min-w-0">
                        <button
                          onClick={() => navigate(`/product/${item.productId}`)}
                          className="font-medium text-foreground line-clamp-2 text-sm text-left hover:underline"
                        >
                          {item.name ?? item.productId}
                        </button>
                        <div className="flex items-center justify-between mt-3">
                          <div className="flex items-center border border-border rounded-lg overflow-hidden">
                            <button
                              onClick={() => onUpdate(item.productId, item.quantity - 1)}
                              aria-label="Decrease quantity"
                              className="w-8 h-8 flex items-center justify-center hover:bg-muted text-muted-foreground transition-colors"
                            >
                              <Minus size={13} />
                            </button>
                            <span className="w-10 text-center text-sm font-medium">
                              {item.quantity}
                            </span>
                            <button
                              onClick={() => onUpdate(item.productId, item.quantity + 1)}
                              aria-label="Increase quantity"
                              className="w-8 h-8 flex items-center justify-center hover:bg-muted text-muted-foreground transition-colors"
                            >
                              <Plus size={13} />
                            </button>
                          </div>
                          <div className="text-right">
                            {isHydrating ? (
                              <div className="animate-pulse space-y-1">
                                <div className="h-4 bg-muted rounded w-20 ml-auto" />
                                {item.quantity > 1 ? <div className="h-3 bg-muted rounded w-14 ml-auto" /> : null}
                              </div>
                            ) : item.price > 0 ? (
                              <>
                                <p className="font-bold" style={{ color: "var(--brand-primary)" }}>
                                  {formatPrice(item.price * item.quantity)}
                                </p>
                                {item.quantity > 1 ? (
                                  <p className="text-xs text-muted-foreground">
                                    {formatPrice(item.price)} / sp
                                  </p>
                                ) : null}
                              </>
                            ) : (
                              <p className="text-xs text-muted-foreground">{t("cart.priceUnavailable")}</p>
                            )}
                          </div>
                        </div>
                      </div>
                      <button
                        onClick={() => onRemove(item.productId)}
                        aria-label={`Remove ${item.name ?? "item"} from cart`}
                        className="p-2 h-fit rounded-lg hover:bg-red-50 text-muted-foreground hover:text-red-500 transition-colors shrink-0"
                      >
                        <Trash2 size={16} />
                      </button>
                    </motion.div>
                  ))}
                </div>
              </motion.div>
            ))}
          </AnimatePresence>

          <button
            onClick={() => navigate("/")}
            className="flex items-center gap-2 text-sm font-medium"
            style={{ color: "var(--brand-primary)" }}
          >
            <ArrowLeft size={16} /> {t("cart.continueShopping")}
          </button>
        </div>

        <div className="space-y-4">
          <div className="bg-card rounded-2xl shadow-sm p-5">
            <div className="flex items-center gap-2 mb-3">
              <Tag size={18} style={{ color: "var(--brand-primary)" }} />
              <h3 className="font-semibold text-foreground">{t("cart.couponHeader")}</h3>
            </div>
            <div className="flex gap-2">
              <input
                value={coupon}
                onChange={(e) => setCoupon(e.target.value.toUpperCase())}
                onKeyDown={(e) => e.key === "Enter" && handleApplyCoupon()}
                placeholder={t("cart.couponPlaceholder")}
                className="flex-1 px-3 py-2.5 border border-border rounded-xl text-sm outline-none focus:border-[var(--brand-primary)] uppercase tracking-wider"
              />
              <button
                onClick={handleApplyCoupon}
                disabled={!coupon.trim() || couponMutation.isPending}
                className="px-4 py-2.5 rounded-xl text-sm font-semibold text-white disabled:opacity-50 disabled:cursor-not-allowed"
                style={{ background: "var(--brand-primary)" }}
              >
                {couponMutation.isPending ? t("cart.couponApplying") : t("cart.couponApply")}
              </button>
            </div>
            <div aria-live="polite" aria-atomic="true">
              {couponError ? <p className="text-xs text-red-600 dark:text-red-400 mt-1.5">{couponError}</p> : null}
              {appliedCoupon ? (
                <div
                  className="mt-2 flex items-center justify-between px-3 py-2 rounded-lg text-sm"
                  style={{ background: "rgba(238,77,45,0.08)" }}
                >
                  <span style={{ color: "var(--brand-primary)" }}>
                    {t("cart.couponApplied", { code: appliedCoupon })}
                    {couponDiscount > 0 ? ` · -${formatPrice(couponDiscount)}` : ""}
                  </span>
                  <button
                    onClick={handleRemoveCoupon}
                    className="text-muted-foreground hover:text-red-400 text-xs"
                  >
                    {t("cart.couponRemove")}
                  </button>
                </div>
              ) : null}
            </div>
            <p className="text-[11px] text-muted-foreground mt-3">{t("cart.couponHint")}</p>
          </div>

          <div className="bg-card rounded-2xl shadow-sm p-5">
            <h3 className="font-bold text-foreground mb-4">{t("cart.summaryTitle")}</h3>
            <div className="space-y-3">
              <div className="flex justify-between text-sm">
                <span className="text-muted-foreground">{t("cart.subtotal", { count: itemCount })}</span>
                <span className="font-medium">{formatPrice(totalAmount)}</span>
              </div>
              {couponDiscount > 0 ? (
                <div className="flex justify-between text-sm">
                  <span className="text-muted-foreground">{t("cart.voucherDiscount")}</span>
                  <span className="font-medium" style={{ color: "var(--brand-primary)" }}>
                    -{formatPrice(couponDiscount)}
                  </span>
                </div>
              ) : null}
              <div className="flex justify-between text-sm">
                <span className="text-muted-foreground">{t("cart.shippingFee")}</span>
                <span className={shippingFee === 0 ? "font-medium text-green-500" : "font-medium"}>
                  {shippingFee === 0 ? t("cart.free") : formatPrice(shippingFee)}
                </span>
              </div>
              <div className="border-t border-border pt-3 flex justify-between">
                <span className="font-bold text-foreground">{t("cart.totalLabel")}</span>
                <div className="text-right">
                  <span className="font-black text-xl" style={{ color: "var(--brand-primary)" }}>
                    {formatPrice(finalTotal)}
                  </span>
                  {couponDiscount > 0 ? (
                    <p className="text-xs" style={{ color: "var(--brand-primary)" }}>
                      {t("cart.savings", { amount: formatPrice(couponDiscount) })}
                    </p>
                  ) : null}
                </div>
              </div>
            </div>

            {!authenticated ? (
              <div
                className="mt-5 mb-3 flex items-center justify-between gap-3 rounded-xl px-4 py-3"
                style={{ background: "rgba(238,77,45,0.08)", border: "1px solid rgba(238,77,45,0.2)" }}
              >
                <div className="flex items-center gap-2 min-w-0">
                  <LogIn size={16} style={{ color: "var(--brand-primary)", flexShrink: 0 }} />
                  <span className="text-sm font-medium" style={{ color: "var(--brand-primary)" }}>
                    {t("cart.guestBanner")}
                  </span>
                </div>
                <button
                  onClick={() => login("/checkout")}
                  className="shrink-0 px-3 py-1.5 rounded-lg text-xs font-semibold text-white"
                  style={{ background: "var(--brand-primary)" }}
                >
                  {t("cart.loginBtn")}
                </button>
              </div>
            ) : null}
            <button
              onClick={() => navigate("/checkout")}
              disabled={!authenticated}
              aria-disabled={!authenticated}
              className="w-full mt-2 py-4 rounded-xl text-white font-bold text-base shadow-lg transition-opacity flex items-center justify-center gap-2 disabled:opacity-40 disabled:cursor-not-allowed hover:enabled:opacity-90"
              style={{ background: "linear-gradient(135deg, var(--brand-primary), #ff8a40)" }}
            >
              {t("cart.proceedCheckout")} <ChevronRight size={18} />
            </button>

            <div className="mt-4 flex items-center justify-center gap-2" aria-hidden="true">
              {config.payment.providers.map((method) => (
                <div
                  key={method}
                  className="px-2 py-1 bg-muted rounded text-[10px] font-medium text-muted-foreground"
                >
                  {method}
                </div>
              ))}
            </div>
          </div>

          <div className="flex items-center gap-2 text-xs text-muted-foreground justify-center">
            <ShieldCheck size={14} style={{ color: "var(--brand-primary)" }} />
            <span>{t("cart.sslNotice")}</span>
          </div>
        </div>
      </div>
    </div>
  );
}
