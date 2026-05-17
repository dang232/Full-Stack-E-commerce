import { z } from "zod";

import { api } from "../client";

const initiatePaymentSchema = z
  .object({
    orderId: z.string(),
    redirectUrl: z.string().url(),
    transactionId: z.string().optional(),
  })
  .loose();

const paymentStatusSchema = z
  .object({
    orderId: z.string(),
    status: z.string(),
    paidAt: z.string().nullable().optional(),
    method: z.string().optional(),
  })
  .loose();
export type PaymentStatus = z.infer<typeof paymentStatusSchema>;

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
