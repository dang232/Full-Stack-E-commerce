import { z } from "zod";

export const walletSchema = z
  .object({
    sellerId: z.string().optional(),
    balance: z.number(),
    pending: z.number().optional(),
    currency: z.string().default("VND"),
    updatedAt: z.string().optional(),
  })
  .passthrough();

export const payoutSchema = z
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
