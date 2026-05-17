import { z } from "zod";

import {
  adminPayoutSchema,
  couponSchema,
  dashboardRevenuePointSchema,
  dashboardSummarySchema,
  dashboardTopProductSchema,
  dashboardTopSellerSchema,
  disputeSchema,
  reviewSchema,
  sellerSummarySchema,
  type DashboardSummary,
} from "../../../types/api";
import type { COUPON_TYPES } from "../../domain-enums";
import { api } from "../client";

export type { DashboardSummary };

export const adminListSellers = () => api.get("/admin/sellers", z.array(sellerSummarySchema));
export const adminApproveSeller = (id: string) =>
  api.post(`/admin/sellers/${encodeURIComponent(id)}/approve`, sellerSummarySchema);

export const adminPendingReviews = () => api.get("/admin/reviews/pending", z.array(reviewSchema));
export const adminApproveReview = (id: string) =>
  api.put(`/admin/reviews/${encodeURIComponent(id)}/approve`, reviewSchema);
export const adminRejectReview = (id: string, body: { reason: string }) =>
  api.put(`/admin/reviews/${encodeURIComponent(id)}/reject`, reviewSchema, body);

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

export const adminOpenDisputes = () => api.get("/admin/disputes/open", z.array(disputeSchema));
export const adminResolveDispute = (
  id: string,
  body: { resolution: string; refundAmount?: number },
) => api.post(`/admin/disputes/${encodeURIComponent(id)}/resolve`, disputeSchema, body);

export const adminPendingPayouts = () =>
  api.get("/admin/finance/payouts/pending", z.array(adminPayoutSchema));
export const adminCompletePayout = (id: string) =>
  api.post(`/admin/finance/payouts/${encodeURIComponent(id)}/complete`, adminPayoutSchema);
export const adminFailPayout = (id: string, body: { reason: string }) =>
  api.post(`/admin/finance/payouts/${encodeURIComponent(id)}/fail`, adminPayoutSchema, body);

// Dashboard
//
// Backend names for the same conceptual KPI vary (`totalRevenue` vs `revenue`).
// The dashboard summary schema (in types/api/admin.ts) accepts every alias and
// the consumer just reads `.totalRevenue`, so the UI no longer needs runtime
// key probing.
export const dashboardSummary = () => api.get("/admin/dashboard/summary", dashboardSummarySchema);
export const dashboardRevenue = (
  params: { from?: string; to?: string; granularity?: "day" | "week" | "month" } = {},
) => api.get("/admin/dashboard/revenue", z.array(dashboardRevenuePointSchema), params);
export const dashboardTopProducts = (params: { limit?: number } = {}) =>
  api.get("/admin/dashboard/top-products", z.array(dashboardTopProductSchema), {
    limit: params.limit ?? 10,
  });
export const dashboardTopSellers = (params: { limit?: number } = {}) =>
  api.get("/admin/dashboard/top-sellers", z.array(dashboardTopSellerSchema), {
    limit: params.limit ?? 10,
  });
