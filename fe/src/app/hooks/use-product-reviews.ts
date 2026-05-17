import { useQuery } from "@tanstack/react-query";

import { reviewsByProduct } from "../lib/api/endpoints/reviews";
import type { Review } from "../types/api";

export function useProductReviews(productId: string) {
  return useQuery<Review[]>({
    queryKey: ["catalog", "reviews", "product", productId],
    queryFn: () => reviewsByProduct(productId),
    enabled: !!productId,
  });
}
