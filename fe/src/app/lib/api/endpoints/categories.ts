import { z } from "zod";

import { categorySchema } from "../../../types/api";
import { api } from "../client";

export const categoryTree = () =>
  api.get("/categories", z.array(categorySchema), undefined, { auth: false });
