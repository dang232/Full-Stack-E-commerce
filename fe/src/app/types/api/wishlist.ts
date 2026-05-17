import { z } from "zod";

const wishlistEntrySchema = z
  .object({
    productId: z.string(),
    createdAt: z.string().optional(),
  })
  .passthrough();

export const wishlistResponseSchema = z
  .object({
    productIds: z.array(z.string()),
    items: z.array(wishlistEntrySchema),
  })
  .passthrough();
export type WishlistResponse = z.infer<typeof wishlistResponseSchema>;

export const wishlistToggleResponseSchema = z
  .object({
    productId: z.string(),
    changed: z.boolean(),
    inWishlist: z.boolean(),
  })
  .passthrough();
export type WishlistToggleResponse = z.infer<typeof wishlistToggleResponseSchema>;

export const wishlistClearResponseSchema = z
  .object({
    removed: z.number(),
  })
  .passthrough();
