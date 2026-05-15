import { z } from "zod";
import { api } from "../client";
import { pageSchema, productDetailSchema, productSummarySchema } from "../../../types/api";

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

export const sellerProductCreate = (body: unknown) =>
  api.post("/sellers/me/products", productDetailSchema, body);

export const sellerProductUpdate = (id: string, body: unknown) =>
  api.put(`/sellers/me/products/${encodeURIComponent(id)}`, productDetailSchema, body);

export const sellerProductImageUploadUrl = (productId: string, body: { contentType: string; size?: number }) =>
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
