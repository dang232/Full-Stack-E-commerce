import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useAuth } from "./use-auth";
import {
  addCartItem,
  clearCart as clearCartApi,
  getCart,
  removeCartItem,
  updateCartItem,
} from "../lib/api/endpoints/cart";
import type { Cart, CartItem } from "../types/api";

const CART_KEY = ["cart"] as const;

const EMPTY_CART: Cart = { items: [], itemCount: 0, totalAmount: 0 };

/** Compute optimistic cart state for an "add" before the server has responded. */
function optimisticAdd(cart: Cart | undefined, productId: string, quantity: number): Cart {
  const base = cart ?? EMPTY_CART;
  const items = [...(base.items ?? [])];
  const existing = items.findIndex((i) => i.productId === productId);
  if (existing >= 0) {
    items[existing] = { ...items[existing], quantity: items[existing].quantity + quantity };
  } else {
    // Skeleton item — name/price/image will reconcile from the server response.
    items.push({ productId, name: "", price: 0, quantity } as CartItem);
  }
  return recomputeTotals({ ...base, items });
}

function optimisticUpdate(cart: Cart | undefined, productId: string, quantity: number): Cart {
  const base = cart ?? EMPTY_CART;
  const items = (base.items ?? []).map((i) =>
    i.productId === productId ? { ...i, quantity } : i,
  );
  return recomputeTotals({ ...base, items });
}

function optimisticRemove(cart: Cart | undefined, productId: string): Cart {
  const base = cart ?? EMPTY_CART;
  const items = (base.items ?? []).filter((i) => i.productId !== productId);
  return recomputeTotals({ ...base, items });
}

function recomputeTotals(cart: Cart): Cart {
  const items = cart.items ?? [];
  return {
    ...cart,
    items,
    itemCount: items.reduce((n, i) => n + i.quantity, 0),
    totalAmount: items.reduce((s, i) => s + i.price * i.quantity, 0),
  };
}

export function useCart() {
  const { authenticated, ready } = useAuth();
  const qc = useQueryClient();

  const query = useQuery<Cart>({
    queryKey: CART_KEY,
    queryFn: getCart,
    enabled: ready && authenticated,
  });

  const addItem = useMutation<Cart, unknown, { productId: string; quantity: number }, { previous?: Cart }>({
    mutationFn: (input) => addCartItem(input),
    onMutate: async (input) => {
      await qc.cancelQueries({ queryKey: CART_KEY });
      const previous = qc.getQueryData<Cart>(CART_KEY);
      qc.setQueryData<Cart>(CART_KEY, (curr) =>
        optimisticAdd(curr, input.productId, input.quantity),
      );
      return { previous };
    },
    onSuccess: (cart) => qc.setQueryData(CART_KEY, cart),
    onError: (_err, _input, context) => {
      if (context?.previous) qc.setQueryData(CART_KEY, context.previous);
      else void qc.invalidateQueries({ queryKey: CART_KEY });
    },
  });

  const updateItem = useMutation<Cart, unknown, { productId: string; quantity: number }, { previous?: Cart }>({
    mutationFn: ({ productId, quantity }) => updateCartItem(productId, { quantity }),
    onMutate: async ({ productId, quantity }) => {
      await qc.cancelQueries({ queryKey: CART_KEY });
      const previous = qc.getQueryData<Cart>(CART_KEY);
      qc.setQueryData<Cart>(CART_KEY, (curr) => optimisticUpdate(curr, productId, quantity));
      return { previous };
    },
    onSuccess: (cart) => qc.setQueryData(CART_KEY, cart),
    onError: (_err, _input, context) => {
      if (context?.previous) qc.setQueryData(CART_KEY, context.previous);
      else void qc.invalidateQueries({ queryKey: CART_KEY });
    },
  });

  const removeItem = useMutation<Cart, unknown, string, { previous?: Cart }>({
    mutationFn: (productId) => removeCartItem(productId),
    onMutate: async (productId) => {
      await qc.cancelQueries({ queryKey: CART_KEY });
      const previous = qc.getQueryData<Cart>(CART_KEY);
      qc.setQueryData<Cart>(CART_KEY, (curr) => optimisticRemove(curr, productId));
      return { previous };
    },
    onSuccess: (cart) => qc.setQueryData(CART_KEY, cart),
    onError: (_err, _input, context) => {
      if (context?.previous) qc.setQueryData(CART_KEY, context.previous);
      else void qc.invalidateQueries({ queryKey: CART_KEY });
    },
  });

  const clear = useMutation<unknown, unknown, void, { previous?: Cart }>({
    mutationFn: () => clearCartApi(),
    onMutate: async () => {
      await qc.cancelQueries({ queryKey: CART_KEY });
      const previous = qc.getQueryData<Cart>(CART_KEY);
      qc.setQueryData<Cart>(CART_KEY, EMPTY_CART);
      return { previous };
    },
    onError: (_err, _input, context) => {
      if (context?.previous) qc.setQueryData(CART_KEY, context.previous);
      else void qc.invalidateQueries({ queryKey: CART_KEY });
    },
  });

  const cart = query.data;
  const items = cart?.items ?? [];
  const itemCount = cart?.itemCount ?? items.reduce((n, i) => n + i.quantity, 0);
  const totalAmount = cart?.totalAmount ?? items.reduce((s, i) => s + i.price * i.quantity, 0);

  return {
    cart,
    items,
    itemCount,
    totalAmount,
    isLoading: query.isLoading,
    error: query.error,
    addItem: addItem.mutate,
    addItemAsync: addItem.mutateAsync,
    updateItem: updateItem.mutate,
    removeItem: removeItem.mutate,
    clear: clear.mutate,
    refetch: query.refetch,
  };
}
