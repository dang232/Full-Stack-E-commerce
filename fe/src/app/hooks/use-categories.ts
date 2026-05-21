import { queryOptions, useQuery } from "@tanstack/react-query";

import { categoryTree } from "../lib/api/endpoints/categories";
import type { Category } from "../types/api";

export const categoriesOptions = () =>
  queryOptions<Category[]>({
    queryKey: ["catalog", "categories"],
    queryFn: () => categoryTree(),
  });

export function useCategories() {
  return useQuery(categoriesOptions());
}

export function categoryDisplayLabel(category: Pick<Category, "id" | "name" | "label">): string {
  return category.label ?? category.name ?? category.id;
}
