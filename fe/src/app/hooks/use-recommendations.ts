import { useQuery } from "@tanstack/react-query";

import {
  frequentlyBoughtTogether,
  youMayAlsoLike,
  type RecommendationItem,
} from "../lib/api/endpoints/recommendations";

const FBT_DEFAULT_LIMIT = 4;
const YMAL_DEFAULT_LIMIT = 8;

/**
 * Co-purchase suggestions for a given product. Returns an empty list when
 * the product has no recorded co-purchases yet (cold start) or the request
 * fails — callers render the section conditionally on length, never an
 * error state, so a missing recommender feels like "no suggestions yet"
 * rather than a broken page.
 */
export function useFrequentlyBoughtTogether(
  productId: string | undefined,
  limit = FBT_DEFAULT_LIMIT,
) {
  return useQuery<RecommendationItem[]>({
    queryKey: ["recommendations", "fbt", productId, limit],
    queryFn: () => frequentlyBoughtTogether(productId ?? "", limit),
    enabled: !!productId,
    staleTime: 5 * 60_000,
    retry: false,
  });
}

/**
 * Same-category, ±30%-price candidates ranked by sales volume / rating.
 * Replaces the previous client-side filter on ProductPage.
 */
export function useYouMayAlsoLike(productId: string | undefined, limit = YMAL_DEFAULT_LIMIT) {
  return useQuery<RecommendationItem[]>({
    queryKey: ["recommendations", "ymal", productId, limit],
    queryFn: () => youMayAlsoLike(productId ?? "", limit),
    enabled: !!productId,
    staleTime: 5 * 60_000,
    retry: false,
  });
}
