import { useQuery } from "@tanstack/react-query";

import { sellerRevenue, type SellerRevenuePoint } from "../lib/api/endpoints/seller-analytics";

import { useAuth } from "./use-auth";

const REVENUE_KEY = (days: number) => ["seller", "revenue", { days }] as const;

interface UseSellerRevenueOptions {
  days?: number;
}

/**
 * Daily revenue + order-count for the current seller. Gated on the SELLER
 * realm role so anonymous or buyer-only sessions never trigger the request.
 */
export function useSellerRevenue({ days = 30 }: UseSellerRevenueOptions = {}) {
  const { ready, authenticated, roles } = useAuth();
  const enabled = ready && authenticated && roles.includes("SELLER");

  const query = useQuery<SellerRevenuePoint[]>({
    queryKey: REVENUE_KEY(days),
    queryFn: () => sellerRevenue({ days }),
    enabled,
    staleTime: 60_000,
    retry: false,
  });

  return {
    points: query.data ?? [],
    isLoading: query.isLoading && enabled,
    error: query.error,
  };
}
