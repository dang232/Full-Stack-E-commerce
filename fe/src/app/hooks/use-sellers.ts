import { queryOptions, useQuery } from "@tanstack/react-query";

import { productList } from "../lib/api/endpoints/products";
import { getSeller } from "../lib/api/endpoints/sellers";
import type { PublicSeller, ProductSummary, Page } from "../types/api";

export const sellerDetailOptions = (id: string | undefined) =>
  queryOptions<PublicSeller>({
    queryKey: ["sellers", "detail", id],
    queryFn: () => getSeller(id!),
    enabled: !!id,
    retry: false,
  });

export const sellerProductsOptions = (sellerId: string | undefined) =>
  queryOptions<Page<ProductSummary>>({
    queryKey: ["catalog", "products", { sellerId }],
    queryFn: () => productList({ sellerId }),
    enabled: !!sellerId,
    retry: false,
  });

export function useSellerDetail(id: string | undefined) {
  return useQuery(sellerDetailOptions(id));
}

export function useSellerProducts(sellerId: string | undefined) {
  return useQuery(sellerProductsOptions(sellerId));
}
