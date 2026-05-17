import { useQuery } from "@tanstack/react-query";

import { categoryTree } from "../lib/api/endpoints/categories";
import type { Category } from "../types/api";

export function useCategories() {
  return useQuery<Category[]>({
    queryKey: ["catalog", "categories"],
    queryFn: () => categoryTree(),
  });
}

export function categoryDisplayLabel(category: Pick<Category, "id" | "name" | "label">): string {
  return category.label ?? category.name ?? category.id;
}
