import { z } from "zod";

import { cartItemSchema } from "./cart";
import { addressSchema } from "./shared";

export const subOrderSchema = z
  .object({
    id: z.string(),
    sellerId: z.string().optional(),
    sellerName: z.string().optional(),
    status: z.string(),
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
    paymentStatus: z.string().optional(),
    paymentMethod: z.string().optional(),
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
    status: z.string(),
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
    status: z.string(),
    items: z.array(z.unknown()).optional(),
    createdAt: z.string().optional(),
  })
  .passthrough();
export type PendingSubOrder = z.infer<typeof pendingSubOrderSchema>;
