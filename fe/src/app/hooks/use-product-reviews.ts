import { queryOptions, useQuery } from "@tanstack/react-query";

import { reviewsByProduct } from "../lib/api/endpoints/reviews";
import type { Review } from "../types/api";

export const productReviewsOptions = (productId: string) =>
  queryOptions<Review[]>({
    queryKey: ["catalog", "reviews", "product", productId] as const,
    queryFn: () => reviewsByProduct(productId),
    enabled: !!productId,
  });

export function useProductReviews(productId: string) {
  return useQuery(productReviewsOptions(productId));
}
