import { z } from "zod";

/**
 * Admin / staff-facing schemas backing the admin control plane. The buyer-side
 * coupon surface lives in `./coupon.ts`; this file's couponSchema is the
 * authoritative shape returned by `/admin/coupons` (strict fields, no field
 * aliasing because admin owns the canonical data).
 */

export const sellerSummarySchema = z
  .object({
    id: z.string(),
    shopName: z.string(),
    status: z.string(),
    appliedAt: z.string().optional(),
  })
  .passthrough();
export type SellerSummary = z.infer<typeof sellerSummarySchema>;

export const couponSchema = z
  .object({
    id: z.string(),
    code: z.string(),
    type: z.string(),
    value: z.number(),
    minOrderValue: z.number().optional(),
    maxDiscount: z.number().optional(),
    startsAt: z.string().optional(),
    endsAt: z.string().optional(),
    active: z.boolean().optional(),
  })
  .passthrough();
export type Coupon = z.infer<typeof couponSchema>;

export const disputeSchema = z
  .object({
    id: z.string(),
    returnId: z.string(),
    status: z.string(),
    description: z.string().optional(),
    createdAt: z.string().optional(),
  })
  .passthrough();
export type Dispute = z.infer<typeof disputeSchema>;

export const adminPayoutSchema = z
  .object({
    id: z.string(),
    sellerId: z.string(),
    amount: z.number(),
    status: z.string(),
    requestedAt: z.string().optional(),
  })
  .passthrough();
export type AdminPayout = z.infer<typeof adminPayoutSchema>;

/**
 * Dashboard summary KPIs. Backend names for the same conceptual KPI vary
 * (`totalRevenue` vs `revenue`). Each field accepts every alias we have seen
 * and the consumer just reads `.totalRevenue`, so the UI no longer needs
 * runtime key probing.
 */
export const dashboardSummarySchema = z
  .object({
    totalRevenue: z.number().optional(),
    revenue: z.number().optional(),
    total: z.number().optional(),
    totalUsers: z.number().optional(),
    users: z.number().optional(),
    totalOrders: z.number().optional(),
    orders: z.number().optional(),
    totalSellers: z.number().optional(),
    sellers: z.number().optional(),
  })
  .passthrough()
  .transform((s) => ({
    totalRevenue: s.totalRevenue ?? s.revenue ?? s.total ?? null,
    totalUsers: s.totalUsers ?? s.users ?? null,
    totalOrders: s.totalOrders ?? s.orders ?? null,
    totalSellers: s.totalSellers ?? s.sellers ?? null,
  }));
export type DashboardSummary = z.infer<typeof dashboardSummarySchema>;

export const dashboardRevenuePointSchema = z
  .object({ date: z.string(), amount: z.number() })
  .passthrough();
export type DashboardRevenuePoint = z.infer<typeof dashboardRevenuePointSchema>;

export const dashboardTopProductSchema = z
  .object({ productId: z.string(), name: z.string().optional(), revenue: z.number() })
  .passthrough();
export type DashboardTopProduct = z.infer<typeof dashboardTopProductSchema>;

export const dashboardTopSellerSchema = z
  .object({ sellerId: z.string(), shopName: z.string().optional(), revenue: z.number() })
  .passthrough();
export type DashboardTopSeller = z.infer<typeof dashboardTopSellerSchema>;
