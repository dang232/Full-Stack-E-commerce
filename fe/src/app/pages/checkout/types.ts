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
  { id: "VNPAY", name: "VNPay", Icon: IconWallet, desc: "Thanh toán qua ví VNPay, QR Code" },
  { id: "MOMO", name: "MoMo", Icon: IconWallet, desc: "Thanh toán qua ví MoMo" },
  { id: "VIETQR", name: "VietQR", Icon: IconQrcode, desc: "Quét QR chuyển khoản ngân hàng" },
  {
    id: "STRIPE",
    name: "Thẻ quốc tế (Stripe)",
    Icon: IconCreditCard,
    desc: "Visa, Mastercard, Amex via Stripe",
  },
  { id: "PAYPAL", name: "PayPal", Icon: IconBrandPaypal, desc: "Thanh toán qua tài khoản PayPal" },
  { id: "BANK", name: "Thẻ ngân hàng", Icon: IconBuildingBank, desc: "Visa, Mastercard, JCB" },
  { id: "COD", name: "Thanh toán khi nhận hàng", Icon: IconCash, desc: "Trả tiền mặt khi nhận hàng" },
];
