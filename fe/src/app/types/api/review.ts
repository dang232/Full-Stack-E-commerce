import { z } from "zod";

import { productIdSchema } from "./branded-ids";

// BE review-service / product-service ReviewResponse(reviewId, productId,
// buyerId, orderId, rating, text, images, verifiedPurchase, helpfulVotes,
// status, createdAt). FE consumers use review.id, review.userId, review.userName,
// review.comment, review.helpful — aliased through the transform.
export const reviewSchema = z
  .object({
    // Legacy FE-facing names
    id: z.string().optional(),
    userId: z.string().optional(),
    userName: z.string().optional(),
    comment: z.string().optional(),
    helpful: z.number().optional(),
    // Live BE names
    reviewId: z.string().optional(),
    buyerId: z.string().optional(),
    text: z.string().optional(),
    helpfulVotes: z.number().optional(),
    orderId: z.string().optional(),
    verifiedPurchase: z.boolean().optional(),
    status: z.string().optional(),
    productId: productIdSchema,
    rating: z.number(),
    images: z.array(z.string()).optional(),
    createdAt: z.string().optional(),
  })
  .passthrough()
  .transform((raw) => ({
    id: raw.id ?? raw.reviewId ?? "",
    productId: raw.productId,
    userId: raw.userId ?? raw.buyerId,
    userName: raw.userName,
    rating: raw.rating,
    comment: raw.comment ?? raw.text,
    images: raw.images,
    helpful: raw.helpful ?? raw.helpfulVotes ?? 0,
    verifiedPurchase: raw.verifiedPurchase,
    status: raw.status,
    createdAt: raw.createdAt,
  }));
export type Review = z.infer<typeof reviewSchema>;

export const questionSchema = z
  .object({
    id: z.string(),
    productId: productIdSchema,
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
