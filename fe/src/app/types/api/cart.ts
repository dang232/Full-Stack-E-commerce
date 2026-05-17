import { z } from "zod";

export const cartItemSchema = z
  .object({
    productId: z.string(),
    name: z.string().optional(),
    image: z.string().optional(),
    price: z.number(),
    quantity: z.number(),
    sellerId: z.string().optional(),
  })
  .passthrough();

export const cartSchema = z
  .object({
    userId: z.string().optional(),
    items: z.array(cartItemSchema).default([]),
    itemCount: z.number().optional(),
    totalAmount: z.number().optional(),
    updatedAt: z.string().optional(),
  })
  .passthrough();
export type Cart = z.infer<typeof cartSchema>;
export type CartItem = z.infer<typeof cartItemSchema>;
