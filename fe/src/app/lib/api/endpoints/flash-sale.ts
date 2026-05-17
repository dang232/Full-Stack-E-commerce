import { z } from "zod";

import { api } from "../client";

/**
 * Inventory-service flash-sale endpoints (FE-PLAN §2 inventory-service / 8083).
 * The `/reserve` call is what the buyer flow needs during a flash-sale checkout;
 * `/stock` and `/release` are present for completeness and admin tooling.
 */

const reserveResponseSchema = z
  .object({
    reservationId: z.string(),
    status: z.string(),
    expiresAt: z.string().optional().nullable(),
  })
  .loose();
export type FlashSaleReservation = z.infer<typeof reserveResponseSchema>;

export const reserveFlashSale = (body: { productId: string; buyerId: string; quantity: number }) =>
  api.post("/flash-sale/reserve", reserveResponseSchema, body);

const stockResponseSchema = z
  .object({
    productId: z.string(),
    stock: z.number(),
  })
  .loose();

export const flashSaleStock = (productId: string) =>
  api.get(`/flash-sale/stock/${encodeURIComponent(productId)}`, stockResponseSchema, undefined, {
    auth: false,
  });

export const releaseFlashSale = (reservationId: string) =>
  api.post(`/flash-sale/release/${encodeURIComponent(reservationId)}`, z.null());

/**
 * Active flash-sale campaigns surfaced by inventory-service. Public endpoint —
 * the home page renders these without authentication. `stockRemaining` may be
 * absent or null when the Redis-backed counter isn't live yet, in which case
 * callers can fall back to per-item {@link flashSaleStock} polling.
 */
const activeFlashSaleCampaignSchema = z
  .object({
    id: z.string(),
    productId: z.string(),
    originalPrice: z.number(),
    salePrice: z.number(),
    stockTotal: z.number(),
    stockRemaining: z.number().nullable().optional(),
    endsAt: z.string(),
  })
  .loose();
export type ActiveFlashSaleCampaign = z.infer<typeof activeFlashSaleCampaignSchema>;

export const listActiveFlashSaleCampaigns = () =>
  api.get("/flash-sale/active", z.array(activeFlashSaleCampaignSchema), undefined, {
    auth: false,
  });
