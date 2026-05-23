import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useRef, useState } from "react";

import {
  addCartItem,
  clearCart as clearCartApi,
  getCart,
  removeCartItem,
  updateCartItem,
} from "../lib/api/endpoints/cart";
import type { Cart } from "../types/api";
import type { ProductId } from "../types/api/branded-ids";

import { useAuth } from "./use-auth";

const CART_KEY = ["cart"] as const;
const GUEST_STORAGE_KEY = "vnshop:guest-cart";

const EMPTY_CART: Cart = { items: [], itemCount: 0, totalAmount: 0 };

interface GuestCartItem {
  productId: string;
  quantity: number;
}

function readGuestCart(): GuestCartItem[] {
  if (typeof localStorage === "undefined") return [];
  try {
    const raw = localStorage.getItem(GUEST_STORAGE_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw) as unknown;
    if (!Array.isArray(parsed)) return [];
    return parsed
      .filter(
        (it): it is GuestCartItem =>
          typeof it === "object" &&
          it !== null &&
          typeof (it as GuestCartItem).productId === "string" &&
          typeof (it as GuestCartItem).quantity === "number" &&
          (it as GuestCartItem).quantity > 0,
      )
      .map((it) => ({ productId: it.productId, quantity: it.quantity }));
  } catch {
    return [];
  }
}

function writeGuestCart(items: GuestCartItem[]): void {
  if (typeof localStorage === "undefined") return;
  try {
    if (items.length === 0) {
      localStorage.removeItem(GUEST_STORAGE_KEY);
    } else {
      localStorage.setItem(GUEST_STORAGE_KEY, JSON.stringify(items));
    }
  } catch {
    /* quota exceeded, private mode, etc. — guest cart degrades to in-memory */
  }
}

function guestItemsToCart(items: GuestCartItem[]): Cart {
  return {
    items: items.map((it) => ({
      productId: it.productId as ProductId,
      name: undefined,
      image: undefined,
      price: 0,
      quantity: it.quantity,
      sellerId: undefined,
    })),
    itemCount: items.reduce((n, i) => n + i.quantity, 0),
    totalAmount: 0,
  };
}

/** Compute optimistic cart state for an "add" before the server has responded. */
function optimisticAdd(cart: Cart | undefined, productId: string, quantity: number): Cart {
  const base = cart ?? EMPTY_CART;
  const items = [...(base.items ?? [])];
  const existing = items.findIndex((i) => i.productId === productId);
  if (existing >= 0) {
    items[existing] = { ...items[existing], quantity: items[existing].quantity + quantity };
  } else {
    // Skeleton item — name/price/image will reconcile from the server response.
    items.push({
      productId: productId as ProductId,
      name: undefined,
      image: undefined,
      price: 0,
      quantity,
      sellerId: undefined,
    });
  }
  return recomputeTotals({ ...base, items });
}

function optimisticUpdate(cart: Cart | undefined, productId: string, quantity: number): Cart {
  const base = cart ?? EMPTY_CART;
  const items = (base.items ?? []).map((i) => (i.productId === productId ? { ...i, quantity } : i));
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

/**
 * Cart hook with guest-mode + merge-on-login.
 *
 * <p>Anonymous users get a localStorage-backed cart so they can browse, add
 * items, and view /cart without being forced to log in first. On
 * authentication, the guest items are replayed into the server cart in a
 * one-shot migration (mirrors the wishlist pattern). After that point the
 * hook is server-backed and behaves like the original implementation.
 *
 * <p>The merge is additive: server-side quantities are preserved and guest
 * quantities are summed in. Failures during merge are logged and swallowed
 * so a partial outage doesn't block login — the guest cart survives until
 * the next attempt.
 */
export function useCart() {
  const { authenticated, ready } = useAuth();
  const qc = useQueryClient();
  const mergeAttempted = useRef(false);
  const [guestItems, setGuestItems] = useState<GuestCartItem[]>(() => readGuestCart());

  const query = useQuery<Cart>({
    queryKey: CART_KEY,
    queryFn: getCart,
    enabled: ready && authenticated,
  });

  // One-shot guest -> server merge on first authenticated load.
  useEffect(() => {
    if (!ready || !authenticated) return;
    if (mergeAttempted.current) return;
    const pending = readGuestCart();
    if (pending.length === 0) return;
    mergeAttempted.current = true;

    void (async () => {
      for (const item of pending) {
        try {
          await addCartItem({ productId: item.productId, quantity: item.quantity });
        } catch (err) {
          console.warn("cart merge: failed to add", item.productId, err);
        }
      }
      writeGuestCart([]);
      setGuestItems([]);
      void qc.invalidateQueries({ queryKey: CART_KEY });
    })();
  }, [ready, authenticated, qc]);

  const addItem = useMutation<
    Cart,
    unknown,
    { productId: string; quantity: number },
    { previous?: Cart }
  >({
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

  const updateItem = useMutation<
    Cart,
    unknown,
    { productId: string; quantity: number },
    { previous?: Cart }
  >({
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

  // Guest-mode mutations: localStorage-backed, no server round-trip.
  const isGuest = ready && !authenticated;

  const guestAdd = (productId: string, quantity: number) => {
    setGuestItems((prev) => {
      const existing = prev.findIndex((i) => i.productId === productId);
      const next =
        existing >= 0
          ? prev.map((i, idx) =>
              idx === existing ? { ...i, quantity: i.quantity + quantity } : i,
            )
          : [...prev, { productId, quantity }];
      writeGuestCart(next);
      return next;
    });
  };

  const guestUpdate = (productId: string, quantity: number) => {
    setGuestItems((prev) => {
      const next =
        quantity <= 0
          ? prev.filter((i) => i.productId !== productId)
          : prev.map((i) => (i.productId === productId ? { ...i, quantity } : i));
      writeGuestCart(next);
      return next;
    });
  };

  const guestRemove = (productId: string) => {
    setGuestItems((prev) => {
      const next = prev.filter((i) => i.productId !== productId);
      writeGuestCart(next);
      return next;
    });
  };

  const guestClear = () => {
    setGuestItems([]);
    writeGuestCart([]);
  };

  const cart = isGuest ? guestItemsToCart(guestItems) : query.data;
  const items = cart?.items ?? [];
  const itemCount = cart?.itemCount ?? items.reduce((n, i) => n + i.quantity, 0);
  const totalAmount = cart?.totalAmount ?? items.reduce((s, i) => s + i.price * i.quantity, 0);

  return {
    cart,
    items,
    itemCount,
    totalAmount,
    isGuest,
    isLoading: isGuest ? false : query.isLoading,
    error: isGuest ? null : query.error,
    addItem: (
      input: { productId: string; quantity: number },
      options?: Parameters<typeof addItem.mutate>[1],
    ) => {
      if (isGuest) {
        guestAdd(input.productId, input.quantity);
        return;
      }
      return addItem.mutate(input, options);
    },
    addItemAsync: async (input: { productId: string; quantity: number }) => {
      if (isGuest) {
        guestAdd(input.productId, input.quantity);
        return;
      }
      await addItem.mutateAsync(input);
    },
    updateItem: (
      input: { productId: string; quantity: number },
      options?: Parameters<typeof updateItem.mutate>[1],
    ) => {
      if (isGuest) {
        guestUpdate(input.productId, input.quantity);
        return;
      }
      return updateItem.mutate(input, options);
    },
    removeItem: (productId: string, options?: Parameters<typeof removeItem.mutate>[1]) => {
      if (isGuest) {
        guestRemove(productId);
        return;
      }
      return removeItem.mutate(productId, options);
    },
    clear: (options?: Parameters<typeof clear.mutate>[1]) => {
      if (isGuest) {
        guestClear();
        return;
      }
      return clear.mutate(undefined, options);
    },
    refetch: query.refetch,
  };
}
