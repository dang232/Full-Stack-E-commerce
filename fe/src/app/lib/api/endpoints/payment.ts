import { initiatePaymentSchema, paymentStatusSchema, type PaymentStatus } from "../../../types/api";
import { api } from "../client";

export type { PaymentStatus };

export const codConfirm = (body: { orderId: string }, idempotencyKey?: string) =>
  api.post("/payment/cod/confirm", paymentStatusSchema, body, { idempotencyKey });

export const vnpayCreate = (
  body: { orderId: string; returnUrl?: string },
  idempotencyKey?: string,
) => api.post("/payment/vnpay/create", initiatePaymentSchema, body, { idempotencyKey });

export const momoCreate = (
  body: { orderId: string; returnUrl?: string },
  idempotencyKey?: string,
) => api.post("/payment/momo/create", initiatePaymentSchema, body, { idempotencyKey });

export const paymentStatus = (orderId: string) =>
  api.get(`/payment/status/${encodeURIComponent(orderId)}`, paymentStatusSchema);
