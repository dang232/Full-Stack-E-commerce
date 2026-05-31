import { z } from "zod";

const facetEntrySchema = z
  .object({
    key: z.string(),
    count: z.number(),
  })
  .passthrough();

export const searchFacetsSchema = z
  .object({
    categories: z.array(facetEntrySchema),
    brands: z.array(facetEntrySchema),
  })
  .passthrough();
export type SearchFacets = z.infer<typeof searchFacetsSchema>;
