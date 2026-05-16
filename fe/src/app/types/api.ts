import { z } from "zod";

export const moneySchema = z
  .object({
    amount: z.number(),
    currency: z.string().default("VND"),
  })
  .passthrough();

export const pageSchema = <T extends z.ZodTypeAny>(item: T) =>
  z
    .object({
      content: z.array(item),
      totalElements: z.number().optional(),
      totalPages: z.number().optional(),
      number: z.number().optional(),
      size: z.number().optional(),
      first: z.boolean().optional(),
      last: z.boolean().optional(),
    })
    .passthrough();

export interface Page<T> {
  content: T[];
  totalElements?: number;
  totalPages?: number;
  number?: number;
  size?: number;
  first?: boolean;
  last?: boolean;
}

export const productSummarySchema = z
  .object({
    id: z.string(),
    name: z.string(),
    price: z.number().optional(),
    originalPrice: z.number().optional(),
    image: z.string().optional(),
    images: z.array(z.string()).optional(),
    category: z.string().optional(),
    sellerId: z.string().optional(),
    sellerName: z.string().optional(),
    rating: z.number().optional(),
    reviewCount: z.number().optional(),
    sold: z.number().optional(),
    stock: z.number().optional(),
  })
  .passthrough();

export const productDetailSchema = productSummarySchema
  .extend({
    description: z.string().optional(),
    colors: z.array(z.string()).optional(),
    sizes: z.array(z.string()).optional(),
    tags: z.array(z.string()).optional(),
  })
  .passthrough();

export type ProductSummary = z.infer<typeof productSummarySchema>;
export type ProductDetail = z.infer<typeof productDetailSchema>;

export const categorySchema = z
  .object({
    id: z.string(),
    name: z.string().optional(),
    label: z.string().optional(),
    parentId: z.string().nullable().optional(),
    children: z.array(z.lazy((): z.ZodType => categorySchema)).optional(),
  })
  .passthrough();
export type Category = z.infer<typeof categorySchema>;

export const reviewSchema = z
  .object({
    id: z.string(),
    productId: z.string(),
    userId: z.string().optional(),
    userName: z.string().optional(),
    rating: z.number(),
    comment: z.string().optional(),
    images: z.array(z.string()).optional(),
    helpful: z.number().optional(),
    createdAt: z.string().optional(),
  })
  .passthrough();
export type Review = z.infer<typeof reviewSchema>;

export const questionSchema = z
  .object({
    id: z.string(),
    productId: z.string(),
    userId: z.string().optional(),
    question: z.string(),
    answer: z.string().nullable().optional(),
    answeredAt: z.string().nullable().optional(),
    createdAt: z.string().optional(),
  })
  .passthrough();
export type Question = z.infer<typeof questionSchema>;

export const addressSchema = z
  .object({
    line1: z.string(),
    line2: z.string().optional(),
    ward: z.string().optional(),
    district: z.string().optional(),
    city: z.string(),
    province: z.string().optional(),
    country: z.string().default("VN"),
    phone: z.string().optional(),
    isDefault: z.boolean().optional(),
  })
  .passthrough();
export type Address = z.infer<typeof addressSchema>;

export const userProfileSchema = z
  .object({
    id: z.string(),
    email: z.string(),
    name: z.string().optional(),
    phone: z.string().optional(),
    avatar: z.string().optional(),
    addresses: z.array(addressSchema).optional(),
    role: z.string().optional(),
  })
  .passthrough();
export type UserProfile = z.infer<typeof userProfileSchema>;

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

export const subOrderSchema = z
  .object({
    id: z.string(),
    sellerId: z.string().optional(),
    sellerName: z.string().optional(),
    status: z.string(),
    items: z.array(cartItemSchema).optional(),
    trackingCode: z.string().nullable().optional(),
    carrier: z.string().nullable().optional(),
    shippingFee: z.number().optional(),
  })
  .passthrough();

export const orderSchema = z
  .object({
    id: z.string(),
    status: z.string(),
    paymentStatus: z.string().optional(),
    paymentMethod: z.string().optional(),
    subtotal: z.number().optional(),
    shippingFee: z.number().optional(),
    discount: z.number().optional(),
    total: z.number(),
    address: addressSchema.optional(),
    subOrders: z.array(subOrderSchema).optional(),
    createdAt: z.string().optional(),
    estimatedDelivery: z.string().nullable().optional(),
  })
  .passthrough();
export type Order = z.infer<typeof orderSchema>;

export const notificationSchema = z
  .object({
    id: z.string(),
    type: z.string().optional(),
    title: z.string().optional(),
    body: z.string().optional(),
    read: z.boolean().optional(),
    createdAt: z.string().optional(),
    deepLink: z.string().nullable().optional(),
  })
  .passthrough();
export type Notification = z.infer<typeof notificationSchema>;
