import { z } from "zod";
import { api } from "../client";
import { reviewSchema } from "../../../types/api";

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

export const adminListCoupons = () => api.get("/admin/coupons", z.array(couponSchema));
export const adminCreateCoupon = (body: unknown) => api.post("/admin/coupons", couponSchema, body);
export const adminUpdateCoupon = (id: string, body: unknown) =>
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
export const adminResolveDispute = (id: string, body: { resolution: string; refundAmount?: number }) =>
  api.post(`/admin/disputes/${encodeURIComponent(id)}/resolve`, disputeSchema, body);

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
const summarySchema = z.record(z.string(), z.number()).or(z.object({}).passthrough());
export const dashboardSummary = () => api.get("/admin/dashboard/summary", summarySchema);
export const dashboardRevenue = (params: { from?: string; to?: string; granularity?: "day" | "week" | "month" } = {}) =>
  api.get(
    "/admin/dashboard/revenue",
    z.array(z.object({ date: z.string(), amount: z.number() }).passthrough()),
    params,
  );
export const dashboardTopProducts = (params: { limit?: number } = {}) =>
  api.get(
    "/admin/dashboard/top-products",
    z.array(z.object({ productId: z.string(), name: z.string().optional(), revenue: z.number() }).passthrough()),
    { limit: params.limit ?? 10 },
  );
export const dashboardTopSellers = (params: { limit?: number } = {}) =>
  api.get(
    "/admin/dashboard/top-sellers",
    z.array(z.object({ sellerId: z.string(), shopName: z.string().optional(), revenue: z.number() }).passthrough()),
    { limit: params.limit ?? 10 },
  );
