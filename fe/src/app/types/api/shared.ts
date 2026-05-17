import { z } from "zod";

export const moneySchema = z
  .object({
    amount: z.number(),
    currency: z.string().default("VND"),
  })
  .passthrough();

export const pageSchema = <T extends z.ZodType>(item: T) =>
  z
    .object({
      content: z.array(item),
      totalElements: z.number().optional(),
      totalPages: z.number().optional(),
      number: z.number().optional(),
      size: z.number().optional(),
      first: z.boolean().optional(),
      last: z.boolean().optional(),
    })
    .passthrough();

export interface Page<T> {
  content: T[];
  totalElements?: number;
  totalPages?: number;
  number?: number;
  size?: number;
  first?: boolean;
  last?: boolean;
}

export const addressSchema = z
  .object({
    line1: z.string(),
    line2: z.string().optional(),
    ward: z.string().optional(),
    district: z.string().optional(),
    city: z.string(),
    province: z.string().optional(),
    country: z.string().default("VN"),
    phone: z.string().optional(),
    isDefault: z.boolean().optional(),
  })
  .passthrough();
export type Address = z.infer<typeof addressSchema>;

/**
 * Generic empty / 204 response. The clear-cart endpoint and a handful of
 * admin actions resolve with no body — accept null, undefined, or {}.
 */
export const emptyResponseSchema = z.union([z.null(), z.undefined(), z.object({}).passthrough()]);
