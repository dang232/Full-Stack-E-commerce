import { useQuery } from "@tanstack/react-query";

import type { Product } from "../components/vnshop-data";
import { productById, productList } from "../lib/api/endpoints/products";
import type { ProductSummary, ProductDetail } from "../types/api";

function pct(originalPrice: number | undefined, price: number | undefined): number | undefined {
  if (!originalPrice || !price || originalPrice <= price) return undefined;
  return Math.round(((originalPrice - price) / originalPrice) * 100);
}

/**
 * Map a server product (summary or detail) into the UI Product shape.
 * Detail-only fields collapse to their defaults when called with a summary.
 */
function fromServer(p: ProductSummary | ProductDetail): Product {
  // Optional ProductDetail-only fields. Reading them off the union directly
  // keeps the types honest (TypeScript narrows them to `... | undefined`)
  // without an unsafe cast.
  const detail = p as Partial<ProductDetail>;
  return {
    id: p.id,
    name: p.name,
    nameEn: p.name,
    price: p.price ?? 0,
    originalPrice: p.originalPrice,
    discount: pct(p.originalPrice, p.price),
    image: p.image ?? p.images?.[0] ?? "",
    images: p.images ?? (p.image ? [p.image] : []),
    category: p.category ?? "",
    categoryLabel: p.category ?? "",
    sellerId: p.sellerId ?? "",
    sellerName: p.sellerName ?? "",
    rating: p.rating ?? 0,
    reviewCount: p.reviewCount ?? 0,
    sold: p.sold ?? 0,
    stock: p.stock ?? 0,
    description: detail.description ?? "",
    colors: detail.colors,
    sizes: detail.sizes,
    shipping: "Tiêu chuẩn",
    shippingFee: 0,
    location: "Việt Nam",
    tags: detail.tags ?? [],
  };
}

/**
 * Catalog list. Returns whatever the backend returns — empty arrays included.
 * Errors propagate so callers can render an error state explicitly.
 */
export function useProducts() {
  return useQuery<Product[]>({
    queryKey: ["catalog", "products", "list"],
    queryFn: async () => {
      const page = await productList({ size: 50 });
      return page.content.map(fromServer);
    },
  });
}

/** Detail of a single product. */
export function useProduct(id: string) {
  return useQuery<Product>({
    queryKey: ["catalog", "products", "detail", id],
    queryFn: async () => fromServer(await productById(id)),
    enabled: !!id,
  });
}
