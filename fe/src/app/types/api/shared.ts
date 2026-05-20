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
    street: z.string(),
    ward: z.string().optional(),
    district: z.string().optional(),
    city: z.string(),
    isDefault: z.boolean().optional(),
    // Kept FE-side only — the form still collects phone for UX, but the
    // user-service Address record drops it on POST and doesn't return it
    // on GET. Treat as optional/best-effort until the BE shape grows.
    phone: z.string().optional(),
  })
  .passthrough();
export type Address = z.infer<typeof addressSchema>;

/**
 * Generic empty / 204 response. The clear-cart endpoint and a handful of
 * admin actions resolve with no body — accept null, undefined, or {}.
 */
export const emptyResponseSchema = z.union([z.null(), z.undefined(), z.object({}).passthrough()]);
