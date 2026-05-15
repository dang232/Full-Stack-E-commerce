import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { cancelOrder, myOrders, orderById } from "../lib/api/endpoints/orders";

export function useMyOrders(params: { page?: number; size?: number; status?: string } = {}) {
  return useQuery({
    queryKey: ["orders", params],
    queryFn: () => myOrders(params),
  });
}

export function useOrder(id: string | undefined) {
  return useQuery({
    queryKey: ["orders", "detail", id],
    queryFn: () => orderById(id!),
    enabled: !!id,
  });
}

export function useCancelOrder() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => cancelOrder(id),
    onSuccess: (_, id) => {
      void qc.invalidateQueries({ queryKey: ["orders"] });
      void qc.invalidateQueries({ queryKey: ["orders", "detail", id] });
    },
  });
}
