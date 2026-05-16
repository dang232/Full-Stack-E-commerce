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
  .passthrough();
export type FlashSaleReservation = z.infer<typeof reserveResponseSchema>;

export const reserveFlashSale = (body: { productId: string; buyerId: string; quantity: number }) =>
  api.post("/flash-sale/reserve", reserveResponseSchema, body);

const stockResponseSchema = z
  .object({
    productId: z.string(),
    stock: z.number(),
  })
  .passthrough();

export const flashSaleStock = (productId: string) =>
  api.get(`/flash-sale/stock/${encodeURIComponent(productId)}`, stockResponseSchema, undefined, {
    auth: false,
  });

export const releaseFlashSale = (reservationId: string) =>
  api.post(`/flash-sale/release/${encodeURIComponent(reservationId)}`, z.null());
