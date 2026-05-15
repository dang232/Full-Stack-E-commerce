import { z } from "zod";
import { api } from "../client";
import { reviewSchema } from "../../../types/api";

export const reviewsByProduct = (productId: string) =>
  api.get(`/reviews/product/${encodeURIComponent(productId)}`, z.array(reviewSchema), undefined, { auth: false });

export interface CreateReviewInput {
  productId: string;
  orderId?: string;
  rating: number;
  comment?: string;
  images?: string[];
}

export const createReview = (body: CreateReviewInput) => api.post("/reviews", reviewSchema, body);

export const voteReviewHelpful = (id: string) =>
  api.put(`/reviews/${encodeURIComponent(id)}/helpful`, reviewSchema);

export const reviewImageUploadUrl = (reviewId: string, body: { contentType: string }) =>
  api.post(
    `/reviews/${encodeURIComponent(reviewId)}/images/upload-url`,
    z.object({ uploadUrl: z.string(), key: z.string().optional() }).passthrough(),
    body,
  );

export const reviewImageActivate = (reviewId: string, body: { key: string }) =>
  api.post(
    `/reviews/${encodeURIComponent(reviewId)}/images/activate`,
    z.object({ url: z.string() }).passthrough(),
    body,
  );
