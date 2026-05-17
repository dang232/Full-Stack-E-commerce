import { z } from "zod";

import { api } from "../client";

/**
 * Seller-scoped analytics from order-service. Currently exposes a daily
 * revenue + order-count series resolved from the JWT principal — admin already
 * has the cross-seller view via the admin dashboard endpoints.
 */
const sellerRevenuePointSchema = z
  .object({
    date: z.string(),
    revenue: z.number(),
    orderCount: z.number(),
  })
  .loose();
export type SellerRevenuePoint = z.infer<typeof sellerRevenuePointSchema>;

export interface SellerRevenueParams {
  /** Window size in days. Backend clamps to 1-365 and defaults to 30. */
  days?: number;
}

export const sellerRevenue = (params: SellerRevenueParams = {}) =>
  api.get("/sellers/me/revenue", z.array(sellerRevenuePointSchema), {
    days: params.days,
  });
