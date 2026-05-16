import { z } from "zod";

import { pageSchema, productDetailSchema, productSummarySchema } from "../../../types/api";
import { api } from "../client";

export interface ProductListParams {
  page?: number;
  size?: number;
  category?: string;
  sort?: string;
}

export const productList = (params: ProductListParams = {}) =>
  api.get(
    "/products",
    pageSchema(productSummarySchema),
    {
      page: params.page,
      size: params.size ?? 24,
      category: params.category,
      sort: params.sort,
    },
    { auth: false },
  );

export const productById = (id: string) =>
  api.get(`/products/${encodeURIComponent(id)}`, productDetailSchema, undefined, { auth: false });

/** Body for create / update on the seller product endpoints. */
export interface SellerProductWriteBody {
  name: string;
  description?: string;
  price: number;
  originalPrice?: number;
  stock: number;
  category?: string;
  images?: string[];
  image?: string;
}

export const sellerProductCreate = (body: SellerProductWriteBody) =>
  api.post("/sellers/me/products", productDetailSchema, body);

export const sellerProductUpdate = (id: string, body: SellerProductWriteBody) =>
  api.put(`/sellers/me/products/${encodeURIComponent(id)}`, productDetailSchema, body);

export const sellerProductImageUploadUrl = (
  productId: string,
  body: { contentType: string; size?: number },
) =>
  api.post(
    `/sellers/me/products/${encodeURIComponent(productId)}/images/upload-url`,
    z.object({ uploadUrl: z.string(), key: z.string().optional() }).passthrough(),
    body,
  );

export const sellerProductImageActivate = (productId: string, body: { key: string }) =>
  api.post(
    `/sellers/me/products/${encodeURIComponent(productId)}/images/activate`,
    z.object({ url: z.string() }).passthrough(),
    body,
  );
