import { useQuery } from "@tanstack/react-query";
import { searchProducts, type SearchParams } from "../lib/api/endpoints/search";
import type { Product } from "../components/vnshop-data";
import type { ProductSummary } from "../types/api";

function pct(originalPrice: number | undefined, price: number | undefined): number | undefined {
  if (!originalPrice || !price || originalPrice <= price) return undefined;
  return Math.round(((originalPrice - price) / originalPrice) * 100);
}

function fromServer(p: ProductSummary): Product {
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
