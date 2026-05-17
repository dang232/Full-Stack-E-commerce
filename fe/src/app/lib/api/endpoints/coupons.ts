import { z } from "zod";

import { api } from "../client";

/**
 * Coupon endpoints exposed to buyers (the public-facing slice of coupon-service).
 * Admin-side CRUD lives in `./admin.ts`. Backend is mid-migration (see FE-PLAN §1
 * issue #5 + §5 churn note); we use loose schemas so server-side renames don't
 * crash the UI.
 */
const couponSchema = z
  .object({
    id: z.union([z.string(), z.number()]).optional(),
    code: z.string(),
    type: z.string().optional(),
    discountType: z.string().optional(),
    value: z.number().optional(),
    discountValue: z.number().optional(),
    minOrderValue: z.number().optional(),
    maxDiscount: z.number().optional(),
    validUntil: z.string().optional(),
    active: z.boolean().optional(),
  })
  .loose();
export type Coupon = z.infer<typeof couponSchema>;

/** Public list of currently active coupons. */
export const listActiveCoupons = () =>
  api.get("/coupons", z.array(couponSchema), undefined, { auth: false });

const validateResponseSchema = z
  .object({
    valid: z.boolean(),
    discount: z.number().optional(),
    message: z.string().optional(),
  })
  .loose();

/**
 * Pre-apply validation. Backend exposes both `/coupons/validate` and the
 * checkout-prefixed alias `/checkout/validate-coupon`; we hit the canonical
 * coupon path here. `checkout.ts#validateCoupon` keeps the checkout alias for
 * callers already wired to that surface.
 */
export const validateCouponCode = (body: { code: string; orderAmount?: number }) =>
  api.post("/coupons/validate", validateResponseSchema, body);

const applyResponseSchema = z
  .object({
    code: z.string(),
    discount: z.number(),
    finalTotal: z.number().optional(),
  })
  .loose();

/** Apply (consumes one usage). Use during order placement, not preview. */
export const applyCoupon = (body: { code: string; orderAmount: number; orderId?: number }) =>
  api.post("/checkout/apply-coupon", applyResponseSchema, body);
