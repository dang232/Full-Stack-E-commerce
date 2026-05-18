import { useQuery } from "@tanstack/react-query";

import { productById, productList } from "../lib/api/endpoints/products";
import type { ProductSummary, ProductDetail } from "../types/api";
import type { Product } from "../types/ui";

function pct(originalPrice: number | undefined, price: number | undefined): number | undefined {
  if (!originalPrice || !price || originalPrice <= price) return undefined;
  return Math.round(((originalPrice - price) / originalPrice) * 100);
}

/** Pull a usable URL out of an image entry (BE shape or string). */
function imageUrl(entry: ProductSummary["images"] extends (infer T)[] | undefined ? T : never): string {
  return typeof entry === "string" ? entry : (entry?.url ?? "");
}

function flattenImages(p: ProductSummary | ProductDetail): string[] {
  const out: string[] = [];
  if (p.images) {
    for (const e of p.images) {
      const url = imageUrl(e);
      if (url) out.push(url);
    }
  }
  if (out.length === 0 && p.image) out.push(p.image);
  return out;
}

/**
 * Map a server product (summary or detail) into the UI Product shape.
 * Detail-only fields collapse to their defaults when called with a summary.
 * The BE returns prices on the first variant (`variants[0].priceAmount`)
 * and not on a top-level `price`, so fall through to that when needed.
 */
function fromServer(p: ProductSummary | ProductDetail): Product {
  const detail = p as Partial<ProductDetail>;
  const firstVariant = p.variants?.[0];
  const variantPrice = firstVariant?.priceAmount;
  const price = p.price ?? variantPrice ?? 0;
  const images = flattenImages(p);
  const primaryImage = images[0] ?? firstVariant?.imageUrl ?? "";
  const stock = p.stock ?? firstVariant?.stockQuantity ?? 0;
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
    stock,
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
