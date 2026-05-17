import { CreditCard, MapPin, Package, Truck } from "lucide-react";
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
  setStep,
}: Props) {
  const { t } = useTranslation();

  return (
    <div className="space-y-4">
      <h2 className="font-bold text-gray-800 text-lg mb-4">{t("checkout.review.header")}</h2>

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
  );
}
