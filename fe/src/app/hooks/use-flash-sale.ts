import { useQuery } from "@tanstack/react-query";

import {
  listActiveFlashSaleCampaigns,
  type ActiveFlashSaleCampaign,
} from "../lib/api/endpoints/flash-sale";

const ACTIVE_KEY = ["flash-sale", "active"] as const;

/**
 * Active flash-sale campaigns from inventory-service. Public — no auth gate.
 * Refreshed once a minute; the per-product `flashSaleStock` poll is the
 * fall-back when individual cards need a live `stockRemaining` value.
 */
export function useFlashSaleCampaigns() {
  const query = useQuery<ActiveFlashSaleCampaign[]>({
    queryKey: ACTIVE_KEY,
    queryFn: () => listActiveFlashSaleCampaigns(),
    staleTime: 60_000,
    retry: false,
  });

  return {
    campaigns: query.data ?? [],
    isLoading: query.isLoading,
    error: query.error,
  };
}
