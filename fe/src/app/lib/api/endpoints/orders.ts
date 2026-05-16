import { z } from "zod";

import { orderSchema, pageSchema } from "../../../types/api";
import type { PaymentMethod } from "../../domain-enums";
import { api } from "../client";

export interface PlaceOrderInput {
  items: { productId: string; quantity: number }[];
  addressId?: number;
  paymentMethod: PaymentMethod;
  couponCode?: string;
  shippingChoices?: { sellerId: string; code: string }[];
  notes?: string;
}

export const placeOrder = (body: PlaceOrderInput, idempotencyKey: string) =>
  api.post("/orders", orderSchema, body, { idempotencyKey });

export const myOrders = (params: { page?: number; size?: number; status?: string } = {}) =>
  api.get("/orders", pageSchema(orderSchema), {
    page: params.page,
    size: params.size ?? 20,
    status: params.status,
  });

export const orderById = (id: string) => api.get(`/orders/${encodeURIComponent(id)}`, orderSchema);

export const cancelOrder = (id: string) =>
  api.delete(`/orders/${encodeURIComponent(id)}/cancel`, orderSchema);

const returnSchema = z
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

export const requestReturn = (body: {
  orderId: string;
  subOrderId: string;
  reason: string;
  itemIds?: string[];
}) => api.post("/returns", returnSchema, body);

export const myReturns = () => api.get("/returns", z.array(returnSchema));

export const escalateDispute = (returnId: string, body: { description: string }) =>
  api.post(`/returns/${encodeURIComponent(returnId)}/disputes`, returnSchema, body);

export const sellerApproveReturn = (returnId: string) =>
  api.post(`/returns/${encodeURIComponent(returnId)}/approve`, returnSchema);
export const sellerRejectReturn = (returnId: string, body: { reason: string }) =>
  api.post(`/returns/${encodeURIComponent(returnId)}/reject`, returnSchema, body);
export const sellerCompleteReturn = (returnId: string) =>
  api.post(`/returns/${encodeURIComponent(returnId)}/complete`, returnSchema);

const pendingSubOrderSchema = z
  .object({
    id: z.string(),
    orderId: z.string(),
    status: z.string(),
    items: z.array(z.unknown()).optional(),
    createdAt: z.string().optional(),
  })
  .passthrough();
export type PendingSubOrder = z.infer<typeof pendingSubOrderSchema>;

export const sellerPendingOrders = () =>
  api.get("/seller/orders/pending", z.array(pendingSubOrderSchema));

export const sellerAcceptOrder = (subOrderId: string) =>
  api.put(`/seller/orders/${encodeURIComponent(subOrderId)}/accept`, pendingSubOrderSchema);
export const sellerRejectOrder = (subOrderId: string, body: { reason: string }) =>
  api.put(`/seller/orders/${encodeURIComponent(subOrderId)}/reject`, pendingSubOrderSchema, body);
export const sellerShipOrder = (
  subOrderId: string,
  body: { carrier: string; trackingNumber: string },
) => api.put(`/seller/orders/${encodeURIComponent(subOrderId)}/ship`, pendingSubOrderSchema, body);
