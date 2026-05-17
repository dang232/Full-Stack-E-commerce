import { z } from "zod";

import {
  calculateCheckoutSchema,
  paymentMethodSchema,
  shippingOptionSchema,
  validateCouponResponseSchema,
  type CheckoutCalculation,
} from "../../../types/api";
import { api } from "../client";

export type { CheckoutCalculation };
export { calculateCheckoutSchema };

export interface CheckoutCalculateInput {
  items: { productId: string; quantity: number }[];
  addressId?: number;
  couponCode?: string;
}

export const calculateCheckout = (body: CheckoutCalculateInput) =>
  api.post("/checkout/calculate", calculateCheckoutSchema, body);

export const paymentMethods = () =>
  api.get("/checkout/payment-methods", z.array(paymentMethodSchema));

export const shippingOptions = (body: {
  items: { productId: string; quantity: number }[];
  addressId?: number;
}) => api.post("/checkout/shipping-options", z.array(shippingOptionSchema), body);

export const validateCoupon = (body: { code: string; subtotal?: number }) =>
  api.post("/checkout/validate-coupon", validateCouponResponseSchema, body);
