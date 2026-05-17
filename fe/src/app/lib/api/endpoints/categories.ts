import { z } from "zod";

import type { Category } from "../../../types/api";
import { api } from "../client";

// product-service currently returns a flat string[] of category ids
// (services/product-service .../ProductController.java findCategories).
// Adapt at the boundary so callers can keep using the richer Category shape;
// when the BE adds tree + label support this map becomes a no-op.
export const categoryTree = async (): Promise<Category[]> => {
  const ids = await api.get("/categories", z.array(z.string()), undefined, { auth: false });
  return ids.map((id) => ({ id }) satisfies Category);
};
