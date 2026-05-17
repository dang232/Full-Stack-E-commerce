import { z } from "zod";

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

/** Pre-signed PUT URL returned by the review image upload endpoint. */
export const reviewImageUploadUrlSchema = z
  .object({ uploadUrl: z.string(), key: z.string().optional() })
  .passthrough();

/** Final CDN URL after activating a previously uploaded review image. */
export const reviewImageActivateSchema = z.object({ url: z.string() }).passthrough();
