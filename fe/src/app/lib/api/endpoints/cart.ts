import { z } from "zod";
import { api } from "../client";
import { cartSchema } from "../../../types/api";

export const getCart = () => api.get("/cart", cartSchema);

export const addCartItem = (body: { productId: string; quantity: number }) =>
  api.post("/cart/items", cartSchema, body);

export const updateCartItem = (productId: string, body: { quantity: number }) =>
  api.put(`/cart/items/${encodeURIComponent(productId)}`, cartSchema, body);

export const removeCartItem = (productId: string) =>
  api.delete(`/cart/items/${encodeURIComponent(productId)}`, cartSchema);

/**
 * The clear-cart endpoint returns 204 / empty body on success. We don't read the
 * value, just the resolved promise.
 */
const emptyResponseSchema = z.union([z.null(), z.undefined(), z.object({}).passthrough()]);
export const clearCart = () => api.delete("/cart", emptyResponseSchema);
