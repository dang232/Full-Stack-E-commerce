import { z } from "zod";

import { productIdSchema, sellerIdSchema } from "./branded-ids";

/**
 * Admin / staff-facing schemas backing the admin control plane. The buyer-side
 * coupon surface lives in `./coupon.ts`; this file's couponSchema is the
 * authoritative shape returned by `/admin/coupons`.
 *
 * Each schema accepts both the FE-legacy field names AND the BE wire shape so
 * server-side renames don't crash admin pages. See SESSION-HANDOVER-pt28
 * gotcha #58.
 */

// BE user-service SellerProfileResponse(id, shopName, bankName, bankAccount,
// approved: boolean, tier, vacationMode). FE wanted `status: string` and
// `appliedAt`; neither exists. Map approved → status string for the UI.
export const sellerSummarySchema = z
  .object({
    id: z.string(),
    shopName: z.string(),
    // Legacy FE-facing fields
    status: z.string().optional(),
    appliedAt: z.string().optional(),
    // Live BE fields
    approved: z.boolean().optional(),
    bankName: z.string().nullable().optional(),
    bankAccount: z.string().nullable().optional(),
    tier: z.string().optional(),
    vacationMode: z.boolean().optional(),
  })
  .passthrough()
  .transform((raw) => ({
    id: raw.id,
    shopName: raw.shopName,
    status: raw.status ?? (raw.approved ? "APPROVED" : "PENDING"),
    appliedAt: raw.appliedAt,
    approved: raw.approved ?? raw.status === "APPROVED",
    bankName: raw.bankName,
    bankAccount: raw.bankAccount,
    tier: raw.tier,
    vacationMode: raw.vacationMode,
  }));
export type SellerSummary = z.infer<typeof sellerSummarySchema>;

// BE coupon-service CouponResponse(id: Long, code, type, value: BigDecimal,
// minOrderValue, maxDiscount, maxUses, currentUses, active, validFrom,
// validUntil). FE expected id:string, startsAt/endsAt — aliased.
export const couponSchema = z
  .object({
    // Both id shapes (Long from BE serializes as number)
    id: z.union([z.string(), z.number()]).transform((v) => String(v)),
    code: z.string(),
    type: z.string(),
    value: z.number(),
    minOrderValue: z.number().nullable().optional(),
    maxDiscount: z.number().nullable().optional(),
    maxUses: z.number().optional(),
    currentUses: z.number().optional(),
    // Legacy + live names
    startsAt: z.string().optional(),
    endsAt: z.string().optional(),
    validFrom: z.string().nullable().optional(),
    validUntil: z.string().nullable().optional(),
    active: z.boolean().optional(),
  })
  .passthrough()
  .transform((raw) => ({
    id: raw.id,
    code: raw.code,
    type: raw.type,
    value: raw.value,
    minOrderValue: raw.minOrderValue,
    maxDiscount: raw.maxDiscount,
    maxUses: raw.maxUses,
    currentUses: raw.currentUses,
    startsAt: raw.startsAt ?? raw.validFrom ?? undefined,
    endsAt: raw.endsAt ?? raw.validUntil ?? undefined,
    active: raw.active,
  }));
export type Coupon = z.infer<typeof couponSchema>;

// BE order-service DisputeResponse(disputeId, returnId, buyerReason,
// sellerResponse, adminResolution, resolvedBy, status). FE wanted id,
// description, createdAt — disputeId→id; description is the buyer's reason.
export const disputeSchema = z
  .object({
    // Legacy
    id: z.string().optional(),
    description: z.string().optional(),
    createdAt: z.string().optional(),
    // Live BE
    disputeId: z.string().optional(),
    buyerReason: z.string().nullable().optional(),
    sellerResponse: z.string().nullable().optional(),
    adminResolution: z.string().nullable().optional(),
    resolvedBy: z.string().nullable().optional(),
    returnId: z.string(),
    status: z.string(),
  })
  .passthrough()
  .transform((raw) => ({
    id: raw.id ?? raw.disputeId ?? "",
    returnId: raw.returnId,
    status: raw.status,
    description: raw.description ?? raw.buyerReason ?? undefined,
    sellerResponse: raw.sellerResponse ?? undefined,
    adminResolution: raw.adminResolution ?? undefined,
    resolvedBy: raw.resolvedBy ?? undefined,
    createdAt: raw.createdAt,
  }));
export type Dispute = z.infer<typeof disputeSchema>;

// BE order-service finance.PayoutResponse(payoutId, sellerId, amount, status,
// createdAt). Same shape as seller-finance PayoutResponse — legacy callers
// expect id + requestedAt.
export const adminPayoutSchema = z
  .object({
    // Legacy
    id: z.string().optional(),
    requestedAt: z.string().optional(),
    // Live BE
    payoutId: z.string().optional(),
    createdAt: z.string().optional(),
    sellerId: sellerIdSchema,
    amount: z.number(),
    status: z.string(),
  })
  .passthrough()
  .transform((raw) => ({
    id: raw.id ?? raw.payoutId ?? "",
    sellerId: raw.sellerId,
    amount: raw.amount,
    status: raw.status,
    requestedAt: raw.requestedAt ?? raw.createdAt,
  }));
export type AdminPayout = z.infer<typeof adminPayoutSchema>;

/**
 * Dashboard summary KPIs. Backend names for the same conceptual KPI vary
 * (`totalRevenue` vs `revenue`). Each field accepts every alias we have seen
 * and the consumer just reads `.totalRevenue`, so the UI no longer needs
 * runtime key probing.
 *
 * NB: order-service DashboardSummaryResponse currently has no totalUsers or
 * totalSellers — those KPIs render as null/em-dash on the dashboard until
 * cross-service aggregation lands. activeBuyers/activeSellers are the actual
 * BE field names; FE doesn't read them yet but we accept them.
 */
export const dashboardSummarySchema = z
  .object({
    totalRevenue: z.number().optional(),
    revenue: z.number().optional(),
    total: z.number().optional(),
    totalUsers: z.number().optional(),
    users: z.number().optional(),
    activeBuyers: z.number().optional(),
    totalOrders: z.number().optional(),
    orders: z.number().optional(),
    totalSellers: z.number().optional(),
    sellers: z.number().optional(),
    activeSellers: z.number().optional(),
    avgOrderValue: z.number().optional(),
    periodStart: z.string().optional(),
    periodEnd: z.string().optional(),
  })
  .passthrough()
  .transform((s) => ({
    totalRevenue: s.totalRevenue ?? s.revenue ?? s.total ?? null,
    totalUsers: s.totalUsers ?? s.users ?? s.activeBuyers ?? null,
    totalOrders: s.totalOrders ?? s.orders ?? null,
    totalSellers: s.totalSellers ?? s.sellers ?? s.activeSellers ?? null,
  }));
export type DashboardSummary = z.infer<typeof dashboardSummarySchema>;

export const dashboardRevenuePointSchema = z
  .object({ date: z.string(), amount: z.number() })
  .passthrough();
export type DashboardRevenuePoint = z.infer<typeof dashboardRevenuePointSchema>;

export const dashboardTopProductSchema = z
  .object({ productId: productIdSchema, name: z.string().optional(), revenue: z.number() })
  .passthrough();
export type DashboardTopProduct = z.infer<typeof dashboardTopProductSchema>;

export const dashboardTopSellerSchema = z
  .object({ sellerId: sellerIdSchema, shopName: z.string().optional(), revenue: z.number() })
  .passthrough();
export type DashboardTopSeller = z.infer<typeof dashboardTopSellerSchema>;
