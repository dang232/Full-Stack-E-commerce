import { z } from "zod";

const sellerBreakdownSchema = z
  .object({
    sellerId: z.string(),
    sellerName: z.string().optional(),
    subtotal: z.number(),
    shippingFee: z.number().optional(),
  })
  .passthrough();

export const calculateCheckoutSchema = z
  .object({
    subtotal: z.number(),
    shippingFee: z.number(),
    discount: z.number().default(0),
    total: z.number(),
    sellerBreakdown: z.array(sellerBreakdownSchema).optional(),
  })
  .passthrough();
export type CheckoutCalculation = z.infer<typeof calculateCheckoutSchema>;

export const paymentMethodSchema = z
  .object({
    code: z.string(),
    name: z.string(),
    description: z.string().optional(),
    enabled: z.boolean().default(true),
  })
  .passthrough();

export const shippingOptionSchema = z
  .object({
    sellerId: z.string().optional(),
    code: z.string(),
    name: z.string(),
    fee: z.number(),
    estimatedDays: z.number().optional(),
  })
  .passthrough();
