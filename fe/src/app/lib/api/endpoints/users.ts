import { z } from "zod";
import { api } from "../client";
import { addressSchema, userProfileSchema, type Address } from "../../../types/api";

export const myProfile = () => api.get("/users/me", userProfileSchema);

export interface UpdateProfileInput {
  name?: string;
  phone?: string;
  avatar?: string;
}
export const updateProfile = (body: UpdateProfileInput) => api.put("/users/me", userProfileSchema, body);

export const addAddress = (body: Address) => api.post("/users/me/addresses", z.array(addressSchema), body);
export const setDefaultAddress = (index: number) =>
  api.put(`/users/me/addresses/${index}/default`, z.array(addressSchema));
export const removeAddress = (index: number) =>
  api.delete(`/users/me/addresses/${index}`, z.array(addressSchema));

// Seller onboarding
export const registerSeller = (body: { shopName: string; description?: string; phone: string }) =>
  api.post("/sellers/register", z.object({ status: z.string() }).passthrough(), body);

export const sellerProfile = () => api.get("/sellers/me", userProfileSchema);
