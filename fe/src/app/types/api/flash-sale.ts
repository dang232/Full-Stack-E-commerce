import { z } from "zod";

/**
 * Inventory-service flash-sale schemas (FE-PLAN §2 inventory-service / 8083).
 * Reservations are produced by `/flash-sale/reserve`; stock is the per-product
 * Redis-backed counter; campaigns are the home-page surface.
 */

export const reserveFlashSaleResponseSchema = z
  .object({
    reservationId: z.string(),
    status: z.string(),
    expiresAt: z.string().optional().nullable(),
  })
  .passthrough();
export type FlashSaleReservation = z.infer<typeof reserveFlashSaleResponseSchema>;

export const flashSaleStockResponseSchema = z
  .object({
    productId: z.string(),
    stock: z.number(),
  })
  .passthrough();

/**
 * Active flash-sale campaign surfaced by inventory-service. Public endpoint —
 * the home page renders these without authentication. `stockRemaining` may be
 * absent or null when the Redis-backed counter isn't live yet, in which case
 * callers can fall back to per-item stock polling.
 */
export const activeFlashSaleCampaignSchema = z
  .object({
    id: z.string(),
    productId: z.string(),
    originalPrice: z.number(),
    salePrice: z.number(),
    stockTotal: z.number(),
    stockRemaining: z.number().nullable().optional(),
    endsAt: z.string(),
  })
  .passthrough();
export type ActiveFlashSaleCampaign = z.infer<typeof activeFlashSaleCampaignSchema>;
