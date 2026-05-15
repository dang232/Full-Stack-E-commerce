import { z } from "zod";
import { api } from "../client";
import { categorySchema } from "../../../types/api";

export const categoryTree = () => api.get("/categories", z.array(categorySchema), undefined, { auth: false });
