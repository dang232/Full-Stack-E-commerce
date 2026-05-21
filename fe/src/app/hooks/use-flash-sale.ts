import { queryOptions, useQueries, useQuery } from "@tanstack/react-query";
import { useMemo } from "react";

import {
  listActiveFlashSaleCampaigns,
  type ActiveFlashSaleCampaign,
} from "../lib/api/endpoints/flash-sale";
import { productById } from "../lib/api/endpoints/products";
import type { ProductDetail } from "../types/api";

export const flashSaleCampaignsOptions = () =>
  queryOptions<ActiveFlashSaleCampaign[]>({
    queryKey: ["flash-sale", "active"],
    queryFn: () => listActiveFlashSaleCampaigns(),
    staleTime: 60_000,
    retry: false,
  });

/**
 * Active flash-sale campaigns from inventory-service. Public — no auth gate.
 * Refreshed once a minute; the per-product `flashSaleStock` poll is the
 * fall-back when individual cards need a live `stockRemaining` value.
 */
export function useFlashSaleCampaigns() {
  const query = useQuery(flashSaleCampaignsOptions());

  return {
    campaigns: query.data ?? [],
    isLoading: query.isLoading,
    error: query.error,
  };
}

export interface FlashSaleItem {
  campaign: ActiveFlashSaleCampaign;
  product: ProductDetail | undefined;
  isLoading: boolean;
  isError: boolean;
}

/**
 * `/flash-sale/active` only carries productId + price + stock + window — the
 * presentation layer joins it with product-service to pick up name and image.
 * We do that join client-side via parallel `useQueries` so inventory-service
 * doesn't take a synchronous cross-service dep on product-service.
 *
 * Items whose product fetch is loading or errored are still surfaced — the
 * caller decides whether to show a placeholder. The campaign's `salePrice`
 * and `originalPrice` remain authoritative; the product's `price` is ignored.
 *
 * Note: product queries use the same key as productDetailOptions so the cache
 * is shared with ProductPage — a product already loaded there won't re-fetch.
 */
export function useFlashSaleWithProducts() {
  const { campaigns, isLoading: campaignsLoading, error } = useFlashSaleCampaigns();

  const productQueries = useQueries({
    queries: campaigns.map((c) => ({
      queryKey: ["catalog", "products", "detail", c.productId] as const,
      queryFn: () => productById(c.productId),
      staleTime: 60_000,
      retry: false,
    })),
  });

  const items = useMemo<FlashSaleItem[]>(
    () =>
      campaigns.map((campaign, i) => {
        const q = productQueries[i];
        return {
          campaign,
          product: q?.data,
          isLoading: q?.isLoading ?? false,
          isError: !!q?.error,
        };
      }),
    [campaigns, productQueries],
  );

  return {
    items,
    isLoading: campaignsLoading,
    error,
  };
}
