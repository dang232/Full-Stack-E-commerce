import { pageSchema, productSummarySchema } from "../../../types/api";
import { api } from "../client";

export interface SearchParams {
  q?: string;
  category?: string;
  brand?: string;
  minPrice?: number;
  maxPrice?: number;
  sort?: string;
  page?: number;
  size?: number;
}

export const searchProducts = (params: SearchParams) =>
  api.get(
    "/search",
    pageSchema(productSummarySchema),
    {
      q: params.q,
      category: params.category,
      brand: params.brand,
      minPrice: params.minPrice,
      maxPrice: params.maxPrice,
      sort: params.sort,
      page: params.page,
      size: params.size ?? 24,
    },
    { auth: false },
  );
