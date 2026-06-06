import {
  IconBrandPaypal,
  IconBuildingBank,
  IconCash,
  IconCircleCheck,
  IconCreditCard,
  IconMapPin,
  IconQrcode,
  IconTruck,
  IconWallet,
} from "@tabler/icons-react";
import type { TFunction } from "i18next";

export type Step = "address" | "shipping" | "payment" | "review" | "success";

export interface CheckoutStepConfig {
  id: Step;
  labelKey: string;
  icon: typeof IconMapPin;
}

export interface ShippingOption {
  id: string;
  name: string;
  desc: string;
  fee: number;
  eta: string;
}

export interface PaymentOption {
  id: "VNPAY" | "MOMO" | "COD" | "BANK" | "STRIPE" | "PAYPAL" | "VIETQR";
  name: string;
  Icon: typeof IconCreditCard;
  desc: string;
}

export const STEPS: CheckoutStepConfig[] = [
  { id: "address", labelKey: "checkout.steps.address", icon: IconMapPin },
  { id: "shipping", labelKey: "checkout.steps.shipping", icon: IconTruck },
  { id: "payment", labelKey: "checkout.steps.payment", icon: IconCreditCard },
  { id: "review", labelKey: "checkout.steps.review", icon: IconCircleCheck },
];

export function makeFallbackShipping(t: TFunction): ShippingOption[] {
  return [
    {
      id: "STANDARD",
      name: t("checkout.shipping.standardName"),
      desc: t("checkout.shipping.fallbackStandard.desc"),
      fee: 30000,
      eta: t("checkout.shipping.fallbackStandard.eta"),
    },
    {
      id: "EXPRESS",
      name: t("checkout.shipping.expressName"),
      desc: t("checkout.shipping.fallbackEconomy.desc"),
      fee: 45000,
      eta: t("checkout.shipping.fallbackEconomy.eta"),
    },
  ];
}

export function makeFallbackPayment(t: TFunction): PaymentOption[] {
  return [
    { id: "VNPAY", name: "VNPay", Icon: IconWallet, desc: t("checkout.payment.vnpayDesc") },
    { id: "MOMO", name: "MoMo", Icon: IconWallet, desc: t("checkout.payment.momoDesc") },
    { id: "VIETQR", name: "VietQR", Icon: IconQrcode, desc: t("checkout.payment.vietqrDesc") },
    {
      id: "STRIPE",
      name: t("checkout.payment.stripeName"),
      Icon: IconCreditCard,
      desc: "Visa, Mastercard, Amex via Stripe",
    },
    { id: "PAYPAL", name: "PayPal", Icon: IconBrandPaypal, desc: t("checkout.payment.paypalDesc") },
    { id: "BANK", name: t("checkout.payment.bankName"), Icon: IconBuildingBank, desc: "Visa, Mastercard, JCB" },
    { id: "COD", name: t("checkout.payment.codName"), Icon: IconCash, desc: t("checkout.payment.codDesc") },
  ];
}

