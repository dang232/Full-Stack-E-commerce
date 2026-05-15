import { z } from "zod";
import { api } from "../client";

const walletSchema = z
  .object({
    sellerId: z.string().optional(),
    balance: z.number(),
    pending: z.number().optional(),
    currency: z.string().default("VND"),
    updatedAt: z.string().optional(),
  })
  .passthrough();

const payoutSchema = z
  .object({
    id: z.string(),
    amount: z.number(),
    status: z.string(),
    requestedAt: z.string().optional(),
    completedAt: z.string().nullable().optional(),
    bankAccount: z.string().optional(),
  })
  .passthrough();
export type Payout = z.infer<typeof payoutSchema>;

export const myWallet = () => api.get("/sellers/me/finance/wallet", walletSchema);
export const myPayouts = () => api.get("/sellers/me/finance/payouts", z.array(payoutSchema));
export const requestPayout = (body: { amount: number; bankAccount: string }) =>
  api.post("/sellers/me/finance/payouts", payoutSchema, body);
