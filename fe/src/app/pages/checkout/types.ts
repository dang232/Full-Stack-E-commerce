import { CheckCircle, CreditCard, MapPin, Truck } from "lucide-react";

export type Step = "address" | "shipping" | "payment" | "review" | "success";

export interface CheckoutStepConfig {
  id: Step;
  labelKey: string;
  icon: typeof MapPin;
}

export interface ShippingOption {
  id: string;
  name: string;
  desc: string;
  fee: number;
  eta: string;
}

export interface PaymentOption {
  id: "VNPAY" | "MOMO" | "COD" | "BANK";
  name: string;
  icon: string;
  desc: string;
}

export const STEPS: CheckoutStepConfig[] = [
  { id: "address", labelKey: "checkout.steps.address", icon: MapPin },
  { id: "shipping", labelKey: "checkout.steps.shipping", icon: Truck },
  { id: "payment", labelKey: "checkout.steps.payment", icon: CreditCard },
  { id: "review", labelKey: "checkout.steps.review", icon: CheckCircle },
];

export const FALLBACK_SHIPPING: ShippingOption[] = [
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

export const FALLBACK_PAYMENT: PaymentOption[] = [
  { id: "VNPAY", name: "VNPay", icon: "💳", desc: "Thanh toán qua ví VNPay, QR Code" },
  { id: "MOMO", name: "MoMo", icon: "💜", desc: "Thanh toán qua ví MoMo" },
  { id: "BANK", name: "Thẻ ngân hàng", icon: "🏦", desc: "Visa, Mastercard, JCB" },
  { id: "COD", name: "Thanh toán khi nhận hàng", icon: "💵", desc: "Trả tiền mặt khi nhận hàng" },
];
