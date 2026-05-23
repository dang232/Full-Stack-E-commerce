import { z } from "zod";

import { orderIdSchema, sellerIdSchema } from "./branded-ids";
import { cartItemSchema } from "./cart";
import { PAYMENT_METHOD_VALUES, PAYMENT_STATUS_VALUES } from "./payment";
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

// BE returns Money as { amount: BigDecimal, currency: String }. FE works with
// bare numbers in VND. Accept both shapes.
const moneyToNumber = z.union([
  z.number(),
  z.object({ amount: z.number(), currency: z.string().optional() }).transform((m) => m.amount),
]);

// BE SubOrderResponse(subOrderId: Long, sellerId, fulfillmentStatus,
// shippingCost: Money, shippingMethod, carrier, trackingNumber, items[])
// FE expects { id: string, status, items[], shippingFee, trackingCode, carrier }.
// The transform aliases the rename pairs so existing OrdersPage / SellerOrders
// consumers don't change.
export const subOrderSchema = z
  .object({
    // Accept either the legacy id or the BE's subOrderId (Long → coerced to string)
    id: z.string().optional(),
    subOrderId: z.union([z.string(), z.number()]).optional(),
    sellerId: sellerIdSchema.optional(),
    sellerName: z.string().optional(),
    // FE used `status`; BE uses `fulfillmentStatus`. Either may appear.
    status: z.enum(FULFILLMENT_STATUS_VALUES).optional(),
    fulfillmentStatus: z.enum(FULFILLMENT_STATUS_VALUES).optional(),
    items: z.array(cartItemSchema).optional(),
    // FE: trackingCode + shippingFee. BE: trackingNumber + shippingCost(Money).
    trackingCode: z.string().nullable().optional(),
    trackingNumber: z.string().nullable().optional(),
    carrier: z.string().nullable().optional(),
    shippingFee: moneyToNumber.optional(),
    shippingCost: moneyToNumber.optional(),
    shippingMethod: z.string().nullable().optional(),
  })
  .passthrough()
  .transform((raw) => ({
    id: raw.id ?? (raw.subOrderId !== undefined ? String(raw.subOrderId) : ""),
    sellerId: raw.sellerId,
    sellerName: raw.sellerName,
    status: raw.status ?? raw.fulfillmentStatus ?? "PENDING_ACCEPTANCE",
    items: raw.items,
    trackingCode: raw.trackingCode ?? raw.trackingNumber ?? null,
    carrier: raw.carrier ?? null,
    shippingFee: raw.shippingFee ?? raw.shippingCost ?? 0,
    shippingMethod: raw.shippingMethod,
  }));

// BE OrderResponse(id, orderNumber, buyerId, shippingAddress, subOrders[],
// itemsTotal: Money, shippingTotal: Money, discount: Money, finalAmount: Money,
// paymentMethod, paymentStatus, idempotencyKey)
// — note: NO order-level status. FE used to require `status: string`, which made
// every parse fail. We now derive a display status from the sub-orders so the
// existing UI checks (`order.status === "delivered" | "shipping" | ...`) still
// work without rewriting every consumer.
function deriveOrderStatus(
  subStatuses: readonly (typeof FULFILLMENT_STATUS_VALUES)[number][],
  paymentStatus: (typeof PAYMENT_STATUS_VALUES)[number] | undefined,
): "pending" | "confirmed" | "shipping" | "delivered" | "cancelled" | "returned" {
  if (subStatuses.length === 0) return "pending";
  if (subStatuses.every((s) => s === "CANCELLED" || s === "REJECTED")) return "cancelled";
  if (subStatuses.every((s) => s === "SHIPPED")) {
    return paymentStatus === "COMPLETED" ? "delivered" : "shipping";
  }
  if (subStatuses.some((s) => s === "SHIPPED" || s === "PACKED")) return "shipping";
  if (subStatuses.some((s) => s === "ACCEPTED")) return "confirmed";
  return "pending";
}

export const orderSchema = z
  .object({
    // GET /orders/{id} returns OrderResponse with `id`. GET /orders returns
    // a Page<OrderListItemResponse> with `orderId` instead. Accept both.
    id: orderIdSchema.optional(),
    orderId: z.string().optional(),
    orderNumber: z.string().optional(),
    buyerId: z.string().optional(),
    sellerId: z.string().optional(),
    // Input status accepts ANY string — consumers run it through
    // parseOrderStatus() to narrow to the UI union. The output of this
    // transform IS one of the UI values though (see deriveOrderStatus).
    status: z.string().optional(),
    paymentStatus: z.enum(PAYMENT_STATUS_VALUES).optional(),
    paymentMethod: z.enum(PAYMENT_METHOD_VALUES).optional(),
    // FE-legacy bare-number fields. BE returns Money objects under different names.
    subtotal: moneyToNumber.optional(),
    itemsTotal: moneyToNumber.optional(),
    shippingFee: moneyToNumber.optional(),
    shippingTotal: moneyToNumber.optional(),
    discount: moneyToNumber.optional(),
    total: moneyToNumber.optional(),
    finalAmount: moneyToNumber.optional(),
    totalAmount: moneyToNumber.optional(),
    itemCount: z.number().optional(),
    address: addressSchema.optional(),
    shippingAddress: addressSchema.optional(),
    subOrders: z.array(subOrderSchema).optional(),
    createdAt: z.string().optional(),
    updatedAt: z.string().optional(),
    estimatedDelivery: z.string().nullable().optional(),
  })
  .passthrough()
  .transform((raw) => {
    const subOrders = raw.subOrders ?? [];
    // For the list endpoint, status is a raw BE string we normalize via the
    // same derivation function with a single-element subStatus array. For the
    // detail endpoint it derives from sub-orders. Either way the output is
    // one of the UI status values.
    const rawStatus = raw.status as string | undefined;
    const isUiStatus = (
      ["pending", "confirmed", "shipping", "delivered", "cancelled", "returned"] as const
    ).includes(rawStatus as never);
    const derivedStatus = isUiStatus
      ? (rawStatus as ReturnType<typeof deriveOrderStatus>)
      : rawStatus
        ? deriveOrderStatus(
            // single-element subStatus from the list endpoint's flat status
            ([rawStatus] as readonly string[]).filter((s): s is (typeof FULFILLMENT_STATUS_VALUES)[number] =>
              (FULFILLMENT_STATUS_VALUES as readonly string[]).includes(s),
            ),
            raw.paymentStatus,
          )
        : deriveOrderStatus(
            subOrders.map((s) => s.status),
            raw.paymentStatus,
          );
    return {
      id: (raw.id ?? raw.orderId ?? "") as ReturnType<typeof orderIdSchema.parse>,
      orderNumber: raw.orderNumber,
      buyerId: raw.buyerId,
      sellerId: raw.sellerId,
      status: derivedStatus,
      paymentStatus: raw.paymentStatus,
      paymentMethod: raw.paymentMethod,
      subtotal: raw.subtotal ?? raw.itemsTotal ?? 0,
      shippingFee: raw.shippingFee ?? raw.shippingTotal ?? 0,
      discount: raw.discount ?? 0,
      total: raw.total ?? raw.finalAmount ?? raw.totalAmount ?? 0,
      itemCount: raw.itemCount,
      address: raw.address ?? raw.shippingAddress,
      subOrders,
      createdAt: raw.createdAt,
      updatedAt: raw.updatedAt,
      estimatedDelivery: raw.estimatedDelivery,
      // Top-level convenience aliases for OrdersPage tracking widget.
      trackingCode: subOrders.find((s) => s.trackingCode)?.trackingCode ?? null,
      carrier: subOrders.find((s) => s.carrier)?.carrier ?? null,
    };
  });
export type Order = z.infer<typeof orderSchema>;

/** Buyer-initiated returns + the seller-side moderation surface. */
export const returnSchema = z
  .object({
    id: z.string(),
    orderId: orderIdSchema,
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
    id: z.string().optional(),
    subOrderId: z.union([z.string(), z.number()]).optional(),
    orderId: orderIdSchema,
    status: z.enum(FULFILLMENT_STATUS_VALUES).optional(),
    fulfillmentStatus: z.enum(FULFILLMENT_STATUS_VALUES).optional(),
    items: z.array(z.unknown()).optional(),
    createdAt: z.string().optional(),
  })
  .passthrough()
  .transform((raw) => ({
    id: raw.id ?? (raw.subOrderId !== undefined ? String(raw.subOrderId) : ""),
    orderId: raw.orderId,
    status: raw.status ?? raw.fulfillmentStatus ?? "PENDING_ACCEPTANCE",
    items: raw.items,
    createdAt: raw.createdAt,
  }));
export type PendingSubOrder = z.infer<typeof pendingSubOrderSchema>;
