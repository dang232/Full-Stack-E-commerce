import { z } from "zod";

export const initiatePaymentSchema = z
  .object({
    orderId: z.string(),
    redirectUrl: z.string().url(),
    transactionId: z.string().optional(),
  })
  .passthrough();

export const paymentStatusSchema = z
  .object({
    orderId: z.string(),
    status: z.string(),
    paidAt: z.string().nullable().optional(),
    method: z.string().optional(),
  })
  .passthrough();
export type PaymentStatus = z.infer<typeof paymentStatusSchema>;
