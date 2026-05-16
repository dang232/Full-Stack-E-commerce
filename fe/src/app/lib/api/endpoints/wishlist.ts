import { z } from "zod";

import { api } from "../client";

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

const wishlistToggleResponseSchema = z
  .object({
    productId: z.string(),
    changed: z.boolean(),
    inWishlist: z.boolean(),
  })
  .passthrough();
export type WishlistToggleResponse = z.infer<typeof wishlistToggleResponseSchema>;

const wishlistClearResponseSchema = z
  .object({
    removed: z.number(),
  })
  .passthrough();

export const getWishlist = () => api.get("/users/me/wishlist", wishlistResponseSchema);

export const addWishlistItem = (productId: string) =>
  api.post("/users/me/wishlist", wishlistToggleResponseSchema, { productId });

export const toggleWishlistItem = (productId: string) =>
  api.post("/users/me/wishlist/toggle", wishlistToggleResponseSchema, { productId });

export const removeWishlistItem = (productId: string) =>
  api.delete(`/users/me/wishlist/${encodeURIComponent(productId)}`, wishlistToggleResponseSchema);

export const clearWishlist = () => api.delete("/users/me/wishlist", wishlistClearResponseSchema);
