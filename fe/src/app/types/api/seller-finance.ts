import { z } from "zod";

import { sellerIdSchema } from "./branded-ids";

export const walletSchema = z
  .object({
    sellerId: sellerIdSchema.optional(),
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
