import { z } from "zod";
import { api } from "../client";

export const calculateCheckoutSchema = z
  .object({
    subtotal: z.number(),
    shippingFee: z.number(),
    discount: z.number().default(0),
    total: z.number(),
    sellerBreakdown: z
      .array(
        z
          .object({
            sellerId: z.string(),
            sellerName: z.string().optional(),
            subtotal: z.number(),
            shippingFee: z.number().optional(),
          })
          .passthrough(),
      )
      .optional(),
  })
  .passthrough();
export type CheckoutCalculation = z.infer<typeof calculateCheckoutSchema>;

export interface CheckoutCalculateInput {
  items: Array<{ productId: string; quantity: number }>;
  addressId?: number;
  couponCode?: string;
}

export const calculateCheckout = (body: CheckoutCalculateInput) =>
  api.post("/checkout/calculate", calculateCheckoutSchema, body);

const paymentMethodSchema = z
  .object({
    code: z.string(),
    name: z.string(),
    description: z.string().optional(),
    enabled: z.boolean().default(true),
  })
  .passthrough();
export const paymentMethods = () => api.get("/checkout/payment-methods", z.array(paymentMethodSchema));

const shippingOptionSchema = z
  .object({
    sellerId: z.string().optional(),
    code: z.string(),
    name: z.string(),
    fee: z.number(),
    estimatedDays: z.number().optional(),
  })
  .passthrough();

export const shippingOptions = (body: { items: Array<{ productId: string; quantity: number }>; addressId?: number }) =>
  api.post("/checkout/shipping-options", z.array(shippingOptionSchema), body);

export const validateCoupon = (body: { code: string; subtotal?: number }) =>
  api.post(
    "/checkout/validate-coupon",
    z.object({ valid: z.boolean(), discount: z.number().optional(), message: z.string().optional() }).passthrough(),
    body,
  );
