import { z } from "zod";

import { reviewSchema } from "../../../types/api";
import type { COUPON_TYPES } from "../../domain-enums";
import { api } from "../client";

const sellerSummarySchema = z
  .object({
    id: z.string(),
    shopName: z.string(),
    status: z.string(),
    appliedAt: z.string().optional(),
  })
  .passthrough();

export const adminListSellers = () => api.get("/admin/sellers", z.array(sellerSummarySchema));
export const adminApproveSeller = (id: string) =>
  api.post(`/admin/sellers/${encodeURIComponent(id)}/approve`, sellerSummarySchema);

export const adminPendingReviews = () => api.get("/admin/reviews/pending", z.array(reviewSchema));
export const adminApproveReview = (id: string) =>
  api.put(`/admin/reviews/${encodeURIComponent(id)}/approve`, reviewSchema);
export const adminRejectReview = (id: string, body: { reason: string }) =>
  api.put(`/admin/reviews/${encodeURIComponent(id)}/reject`, reviewSchema, body);

const couponSchema = z
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

export interface CouponWriteBody {
  code: string;
  type: (typeof COUPON_TYPES)[number];
  value: number;
  minOrderValue?: number;
  maxDiscount?: number;
  startsAt?: string;
  endsAt?: string;
  active?: boolean;
}

export const adminListCoupons = () => api.get("/admin/coupons", z.array(couponSchema));
export const adminCreateCoupon = (body: CouponWriteBody) =>
  api.post("/admin/coupons", couponSchema, body);
export const adminUpdateCoupon = (id: string, body: CouponWriteBody) =>
  api.put(`/admin/coupons/${encodeURIComponent(id)}`, couponSchema, body);
export const adminDeactivateCoupon = (id: string) =>
  api.post(`/admin/coupons/${encodeURIComponent(id)}/deactivate`, couponSchema);

const disputeSchema = z
  .object({
    id: z.string(),
    returnId: z.string(),
    status: z.string(),
    description: z.string().optional(),
    createdAt: z.string().optional(),
  })
  .passthrough();
export const adminOpenDisputes = () => api.get("/admin/disputes/open", z.array(disputeSchema));
export const adminResolveDispute = (
  id: string,
  body: { resolution: string; refundAmount?: number },
) => api.post(`/admin/disputes/${encodeURIComponent(id)}/resolve`, disputeSchema, body);

const adminPayoutSchema = z
  .object({
    id: z.string(),
    sellerId: z.string(),
    amount: z.number(),
    status: z.string(),
    requestedAt: z.string().optional(),
  })
  .passthrough();
export const adminPendingPayouts = () =>
  api.get("/admin/finance/payouts/pending", z.array(adminPayoutSchema));
export const adminCompletePayout = (id: string) =>
  api.post(`/admin/finance/payouts/${encodeURIComponent(id)}/complete`, adminPayoutSchema);
export const adminFailPayout = (id: string, body: { reason: string }) =>
  api.post(`/admin/finance/payouts/${encodeURIComponent(id)}/fail`, adminPayoutSchema, body);

// Dashboard
//
// Backend names for the same conceptual KPI vary (`totalRevenue` vs `revenue`).
// Each field accepts every alias we have seen and the consumer just reads
// `.totalRevenue`, so the UI no longer needs runtime key probing.
const dashboardSummarySchema = z
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
export const dashboardSummary = () => api.get("/admin/dashboard/summary", dashboardSummarySchema);
export const dashboardRevenue = (
  params: { from?: string; to?: string; granularity?: "day" | "week" | "month" } = {},
) =>
  api.get(
    "/admin/dashboard/revenue",
    z.array(z.object({ date: z.string(), amount: z.number() }).passthrough()),
    params,
  );
export const dashboardTopProducts = (params: { limit?: number } = {}) =>
  api.get(
    "/admin/dashboard/top-products",
    z.array(
      z
        .object({ productId: z.string(), name: z.string().optional(), revenue: z.number() })
        .passthrough(),
    ),
    { limit: params.limit ?? 10 },
  );
export const dashboardTopSellers = (params: { limit?: number } = {}) =>
  api.get(
    "/admin/dashboard/top-sellers",
    z.array(
      z
        .object({ sellerId: z.string(), shopName: z.string().optional(), revenue: z.number() })
        .passthrough(),
    ),
    { limit: params.limit ?? 10 },
  );
