import { z } from "zod";

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

/** Pre-signed PUT URL returned by the seller product image upload endpoint. */
export const productImageUploadUrlSchema = z
  .object({ uploadUrl: z.string(), key: z.string().optional() })
  .passthrough();

/** Final CDN URL after activating a previously uploaded product image. */
export const productImageActivateSchema = z.object({ url: z.string() }).passthrough();
