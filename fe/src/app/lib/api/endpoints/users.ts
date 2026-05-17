import { z } from "zod";

import { userProfileSchema, type Address } from "../../../types/api";
import { api } from "../client";

export const myProfile = () => api.get("/users/me", userProfileSchema);

export interface UpdateProfileInput {
  name?: string;
  phone?: string;
  avatar?: string;
}
export const updateProfile = (body: UpdateProfileInput) =>
  api.put("/users/me", userProfileSchema, body);

// All three address endpoints return the up-to-date BuyerProfileResponse so the
// client never has to merge partial state — see UserController#addAddress et al.
export const addAddress = (body: Address) =>
  api.post("/users/me/addresses", userProfileSchema, body);
export const setDefaultAddress = (index: number) =>
  api.put(`/users/me/addresses/${index}/default`, userProfileSchema);
export const removeAddress = (index: number) =>
  api.delete(`/users/me/addresses/${index}`, userProfileSchema);

// Seller onboarding
export const registerSeller = (body: { shopName: string; description?: string; phone: string }) =>
  api.post("/sellers/register", z.object({ status: z.string() }).loose(), body);

export const sellerProfile = () => api.get("/sellers/me", userProfileSchema);
