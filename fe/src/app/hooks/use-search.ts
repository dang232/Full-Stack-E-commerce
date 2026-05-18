import { keepPreviousData, useQuery } from "@tanstack/react-query";

import { searchProducts, type SearchParams } from "../lib/api/endpoints/search";
import type { ProductSummary } from "../types/api";
import type { Product } from "../types/ui";

function pct(originalPrice: number | undefined, price: number | undefined): number | undefined {
  if (!originalPrice || !price || originalPrice <= price) return undefined;
  return Math.round(((originalPrice - price) / originalPrice) * 100);
}

function fromServer(p: ProductSummary): Product {
  // ProductSummary.images can be string[] (legacy/search shape) or
  // {url, alt, sortOrder}[] (product-service shape). Normalise to string[]
  // so the UI Product type stays a thin string-array on `images`.
  const images: string[] = (p.images ?? [])
    .map((entry) => (typeof entry === "string" ? entry : entry?.url ?? ""))
    .filter((url): url is string => !!url);
  const firstVariant = p.variants?.[0];
  const primaryImage = p.image ?? images[0] ?? firstVariant?.imageUrl ?? "";
  const price = p.price ?? firstVariant?.priceAmount ?? 0;
  return {
    id: p.id,
    name: p.name,
    nameEn: p.name,
    price,
    originalPrice: p.originalPrice,
    discount: pct(p.originalPrice, price),
    image: primaryImage,
    images: images.length > 0 ? images : primaryImage ? [primaryImage] : [],
    category: p.category ?? p.categoryId ?? "",
    categoryLabel: p.category ?? p.categoryId ?? "",
    sellerId: p.sellerId ?? "",
    sellerName: p.sellerName ?? "",
    rating: p.rating ?? 0,
    reviewCount: p.reviewCount ?? 0,
    sold: p.sold ?? 0,
    stock: p.stock ?? firstVariant?.stockQuantity ?? 0,
    description: "",
    shipping: "Tiêu chuẩn",
    shippingFee: 0,
    location: "Việt Nam",
    tags: [],
  };
}

export interface UseSearchResult {
  products: Product[];
  totalElements: number;
  totalPages: number;
  isLoading: boolean;
  /** Raw error from the search endpoint, if any. Callers decide whether to fall back. */
  error: unknown;
}

/** Backend-driven search. Caller is responsible for any local fallback when error is set. */
export function useSearch(params: SearchParams, enabled = true): UseSearchResult {
  const query = useQuery({
    queryKey: ["search", params],
    queryFn: () => searchProducts(params),
    enabled,
    retry: false,
    // Keep the previous successful page visible during refetches so callers
    // don't flicker back to a local mock catalog on every filter change.
    placeholderData: keepPreviousData,
  });

  const products = (query.data?.content ?? []).map(fromServer);
  const totalElements = query.data?.totalElements ?? products.length;
  const totalPages = query.data?.totalPages ?? 1;

  return {
    products,
    totalElements,
    totalPages,
    isLoading: query.isLoading,
    error: query.error,
  };
}
