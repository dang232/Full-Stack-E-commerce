import { queryOptions, useQuery } from "@tanstack/react-query";

import { productById, productList } from "../lib/api/endpoints/products";
import { fromServer } from "../lib/api/product-mapper";
import type { Product } from "../types/ui";

export const productListOptions = () =>
  queryOptions<Product[]>({
    queryKey: ["catalog", "products", "list"],
    queryFn: async () => {
      const page = await productList({ size: 50 });
      return page.content.map(fromServer);
    },
  });

export const productDetailOptions = (id: string) =>
  queryOptions<Product>({
    queryKey: ["catalog", "products", "detail", id],
    queryFn: async () => fromServer(await productById(id)),
    enabled: !!id,
  });

/**
 * Catalog list. Returns whatever the backend returns — empty arrays included.
 * Errors propagate so callers can render an error state explicitly.
 */
export function useProducts() {
  return useQuery(productListOptions());
}

/** Detail of a single product. */
export function useProduct(id: string) {
  return useQuery(productDetailOptions(id));
}
