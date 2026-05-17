import { z } from "zod";

import { addressSchema } from "./shared";

export const userProfileSchema = z
  .object({
    id: z.string(),
    email: z.string(),
    name: z.string().optional(),
    phone: z.string().optional(),
    avatar: z.string().optional(),
    addresses: z.array(addressSchema).optional(),
    role: z.string().optional(),
  })
  .passthrough();
export type UserProfile = z.infer<typeof userProfileSchema>;

/** Response shape for `POST /sellers/register`. */
export const sellerRegisterResponseSchema = z.object({ status: z.string() }).passthrough();
