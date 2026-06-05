import { IconCreditCard, IconMapPin, IconPackage, IconTruck } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

import { formatPrice } from "../../lib/format";
import type { Address, CartItem } from "../../types/api";

import { formatAddressLine } from "./format";
import type { PaymentOption, ShippingOption } from "./types";

interface Props {
  addresses: Address[];
  selectedAddressIndex: number;
  shipping: ShippingOption | undefined;
  paymentOptions: PaymentOption[];
  selectedPaymentId: PaymentOption["id"];
  cartItems: CartItem[];
  buyerName: string;
  isProcessing: boolean;
  setStep: (step: "address" | "shipping" | "payment") => void;
}

export function CheckoutReviewStep({
  addresses,
  selectedAddressIndex,
  shipping,
  paymentOptions,
  selectedPaymentId,
  cartItems,
  buyerName,
  isProcessing,
  setStep,
}: Props) {
  const { t } = useTranslation();

  return (
    <div className="space-y-4">
      <h2 className="font-bold text-foreground text-lg mb-4">{t("checkout.review.header")}</h2>

      <div className="bg-card rounded-2xl p-4 shadow-sm">
        <div className="flex items-center justify-between mb-3">
          <h3 className="font-semibold text-foreground flex items-center gap-2">
            <IconMapPin size={15} style={{ color: "#00BFB3" }} />{" "}
            {t("checkout.review.shippingAddress")}
          </h3>
          <button
            onClick={() => setStep("address")}
            disabled={isProcessing}
            className="text-xs disabled:opacity-40 disabled:cursor-not-allowed"
            style={{ color: "#00BFB3" }}
          >
            {t("checkout.review.change")}
          </button>
        </div>
        {addresses[selectedAddressIndex] ? (
          <div className="text-sm">
            <p className="font-semibold text-foreground">
              {buyerName}
              {addresses[selectedAddressIndex].phone ? (
                <> · {addresses[selectedAddressIndex].phone}</>
              ) : null}
            </p>
            <p className="text-muted-foreground mt-0.5">
              {formatAddressLine(addresses[selectedAddressIndex])}
            </p>
          </div>
        ) : (
          <p className="text-sm text-red-600 dark:text-red-400">{t("checkout.review.noAddress")}</p>
        )}
      </div>

      <div className="bg-card rounded-2xl p-4 shadow-sm">
        <div className="flex items-center justify-between mb-2">
          <h3 className="font-semibold text-foreground flex items-center gap-2">
            <IconTruck size={15} style={{ color: "#00BFB3" }} />{" "}
            {t("checkout.review.shippingMethod")}
          </h3>
          <button
            onClick={() => setStep("shipping")}
            disabled={isProcessing}
            className="text-xs disabled:opacity-40 disabled:cursor-not-allowed"
            style={{ color: "#00BFB3" }}
          >
            {t("checkout.review.change")}
          </button>
        </div>
        <p className="text-sm font-medium text-foreground">{shipping?.name}</p>
        <p className="text-xs text-muted-foreground">
          {t("checkout.shipping.etaPrefix", { eta: shipping?.eta ?? "" })}
        </p>
      </div>

      <div className="bg-card rounded-2xl p-4 shadow-sm">
        <div className="flex items-center justify-between mb-2">
          <h3 className="font-semibold text-foreground flex items-center gap-2">
            <IconCreditCard size={15} style={{ color: "#00BFB3" }} />{" "}
            {t("checkout.review.paymentMethod")}
          </h3>
          <button
            onClick={() => setStep("payment")}
            disabled={isProcessing}
            className="text-xs disabled:opacity-40 disabled:cursor-not-allowed"
            style={{ color: "#00BFB3" }}
          >
            {t("checkout.review.change")}
          </button>
        </div>
        <p className="text-sm font-medium text-foreground">
          {paymentOptions.find((p) => p.id === selectedPaymentId)?.name}
        </p>
      </div>

      <div className="bg-card rounded-2xl p-4 shadow-sm">
        <h3 className="font-semibold text-foreground flex items-center gap-2 mb-3">
          <IconPackage size={15} style={{ color: "#00BFB3" }} />{" "}
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
                <div className="w-12 h-12 rounded-lg bg-muted" />
              )}
              <div className="flex-1 min-w-0">
                <p className="text-sm font-medium text-foreground truncate">
                  {item.name ?? item.productId}
                </p>
                <p className="text-xs text-muted-foreground">x{item.quantity}</p>
              </div>
              <span className="text-sm font-bold" style={{ color: "#FF6200" }}>
                {formatPrice(item.price * item.quantity)}
              </span>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
