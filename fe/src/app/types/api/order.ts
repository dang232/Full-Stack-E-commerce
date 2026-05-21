import { z } from "zod";

import { cartItemSchema } from "./cart";
import { addressSchema } from "./shared";

/** Wire values emitted by order-service FulfillmentStatus.name() */
export const FULFILLMENT_STATUS_VALUES = [
  "PENDING_ACCEPTANCE",
  "ACCEPTED",
  "PACKED",
  "SHIPPED",
  "REJECTED",
  "CANCELLED",
] as const;

/** Wire values emitted by order-service ReturnStatus.name() */
export const RETURN_STATUS_VALUES = [
  "REQUESTED",
  "APPROVED",
  "REJECTED",
  "COMPLETED",
] as const;

/** Wire values emitted by order-service PaymentStatus.name() */
export const ORDER_PAYMENT_STATUS_VALUES = ["PENDING", "COMPLETED", "FAILED"] as const;

/** Wire values emitted by payment-service PaymentMethod.name() */
export const ORDER_PAYMENT_METHOD_VALUES = [
  "COD",
  "VNPAY",
  "MOMO",
  "VIETQR",
  "STRIPE",
  "PAYPAL",
] as const;

export const subOrderSchema = z
  .object({
    id: z.string(),
    sellerId: z.string().optional(),
    sellerName: z.string().optional(),
    status: z.enum(FULFILLMENT_STATUS_VALUES),
    items: z.array(cartItemSchema).optional(),
    trackingCode: z.string().nullable().optional(),
    carrier: z.string().nullable().optional(),
    shippingFee: z.number().optional(),
  })
  .passthrough();

export const orderSchema = z
  .object({
    id: z.string(),
    status: z.string(),
    paymentStatus: z.enum(ORDER_PAYMENT_STATUS_VALUES).optional(),
    paymentMethod: z.enum(ORDER_PAYMENT_METHOD_VALUES).optional(),
    subtotal: z.number().optional(),
    shippingFee: z.number().optional(),
    discount: z.number().optional(),
    total: z.number(),
    address: addressSchema.optional(),
    subOrders: z.array(subOrderSchema).optional(),
    createdAt: z.string().optional(),
    estimatedDelivery: z.string().nullable().optional(),
  })
  .passthrough();
export type Order = z.infer<typeof orderSchema>;

/** Buyer-initiated returns + the seller-side moderation surface. */
export const returnSchema = z
  .object({
    id: z.string(),
    orderId: z.string(),
    status: z.enum(RETURN_STATUS_VALUES),
    reason: z.string().optional(),
    refundAmount: z.number().optional(),
    createdAt: z.string().optional(),
  })
  .passthrough();
export type Return = z.infer<typeof returnSchema>;

/** Seller-side pending order queue — sub-order items kept loose so we don't
 * crash on shape changes from order-service. */
export const pendingSubOrderSchema = z
  .object({
    id: z.string(),
    orderId: z.string(),
    status: z.enum(FULFILLMENT_STATUS_VALUES),
    items: z.array(z.unknown()).optional(),
    createdAt: z.string().optional(),
  })
  .passthrough();
export type PendingSubOrder = z.infer<typeof pendingSubOrderSchema>;
