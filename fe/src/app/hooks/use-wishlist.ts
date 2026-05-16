import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useRef } from "react";

import {
  addWishlistItem,
  clearWishlist as clearWishlistApi,
  getWishlist,
  removeWishlistItem,
  toggleWishlistItem,
  type WishlistResponse,
} from "../lib/api/endpoints/wishlist";

import { useAuth } from "./use-auth";

const WISHLIST_KEY = ["wishlist"] as const;
const LEGACY_STORAGE_KEY = "vnshop:wishlist";

const EMPTY: WishlistResponse = { productIds: [], items: [] };

/**
 * Server-backed wishlist via /users/me/wishlist. The hook lazily migrates the
 * legacy localStorage list (vnshop:wishlist) once on first authenticated load,
 * so users keep their saved items across the cutover.
 */
export function useWishlist() {
  const { ready, authenticated } = useAuth();
  const qc = useQueryClient();
  const migrationAttempted = useRef(false);

  const query = useQuery<WishlistResponse>({
    queryKey: WISHLIST_KEY,
    queryFn: getWishlist,
    enabled: ready && authenticated,
    initialData: EMPTY,
    initialDataUpdatedAt: 0,
    refetchOnWindowFocus: true,
    retry: false,
  });

  // One-shot migration: if the user has a non-empty legacy localStorage entry,
  // POST each item to the BE, then clear the local cache. Failures are logged
  // and ignored — the user's local list survives until the next attempt.
  useEffect(() => {
    if (!ready || !authenticated) return;
    if (migrationAttempted.current) return;
    migrationAttempted.current = true;

    let legacyIds: string[] = [];
    try {
      const raw = localStorage.getItem(LEGACY_STORAGE_KEY);
      if (raw) {
        const parsed = JSON.parse(raw) as unknown;
        if (Array.isArray(parsed) && parsed.every((x) => typeof x === "string")) {
          legacyIds = parsed;
        }
      }
    } catch {
      /* ignore */
    }
    if (legacyIds.length === 0) return;

    void (async () => {
      const current = qc.getQueryData<WishlistResponse>(WISHLIST_KEY) ?? EMPTY;
      const knownIds = new Set(current.productIds);
      const missing = legacyIds.filter((id) => !knownIds.has(id));
      for (const id of missing) {
        try {
          await addWishlistItem(id);
        } catch (err) {
          console.warn("wishlist migration: failed to add", id, err);
        }
      }
      try {
        localStorage.removeItem(LEGACY_STORAGE_KEY);
      } catch {
        /* ignore */
      }
      void qc.invalidateQueries({ queryKey: WISHLIST_KEY });
    })();
  }, [ready, authenticated, qc]);

  const data = query.data ?? EMPTY;

  const patchAfterToggle = (productId: string, inWishlist: boolean) => {
    qc.setQueryData<WishlistResponse>(WISHLIST_KEY, (prev) => {
      const base = prev ?? EMPTY;
      const has = base.productIds.includes(productId);
      if (inWishlist === has) return base;
      if (inWishlist) {
        return {
          ...base,
          productIds: [productId, ...base.productIds],
          items: [{ productId }, ...base.items],
        };
      }
      return {
        ...base,
        productIds: base.productIds.filter((id) => id !== productId),
        items: base.items.filter((item) => item.productId !== productId),
      };
    });
  };

  const toggleMutation = useMutation<
    { inWishlist: boolean },
    unknown,
    string,
    { previous?: WishlistResponse; nextInWishlist: boolean }
  >({
    mutationFn: async (productId) => toggleWishlistItem(productId),
    onMutate: async (productId) => {
      await qc.cancelQueries({ queryKey: WISHLIST_KEY });
      const previous = qc.getQueryData<WishlistResponse>(WISHLIST_KEY);
      const has = previous?.productIds.includes(productId) ?? false;
      const nextInWishlist = !has;
      patchAfterToggle(productId, nextInWishlist);
      return { previous, nextInWishlist };
    },
    onSuccess: (result, productId) => {
      patchAfterToggle(productId, result.inWishlist);
    },
    onError: (_err, _id, context) => {
      if (context?.previous) qc.setQueryData(WISHLIST_KEY, context.previous);
    },
  });

  const removeMutation = useMutation<unknown, unknown, string, { previous?: WishlistResponse }>({
    mutationFn: removeWishlistItem,
    onMutate: async (productId) => {
      await qc.cancelQueries({ queryKey: WISHLIST_KEY });
      const previous = qc.getQueryData<WishlistResponse>(WISHLIST_KEY);
      patchAfterToggle(productId, false);
      return { previous };
    },
    onError: (_err, _id, context) => {
      if (context?.previous) qc.setQueryData(WISHLIST_KEY, context.previous);
    },
  });

  const clearMutation = useMutation<unknown, unknown, void, { previous?: WishlistResponse }>({
    mutationFn: () => clearWishlistApi(),
    onMutate: async () => {
      await qc.cancelQueries({ queryKey: WISHLIST_KEY });
      const previous = qc.getQueryData<WishlistResponse>(WISHLIST_KEY);
      qc.setQueryData<WishlistResponse>(WISHLIST_KEY, EMPTY);
      return { previous };
    },
    onError: (_err, _v, context) => {
      if (context?.previous) qc.setQueryData(WISHLIST_KEY, context.previous);
    },
  });

  /**
   * @returns true when the toggle resulted in the product being on the wishlist.
   *   The mutation is fire-and-forget; the cache is patched optimistically and
   *   reconciled with the server response once it lands.
   */
  const toggle = (productId: string): boolean => {
    const has = data.productIds.includes(productId);
    toggleMutation.mutate(productId);
    return !has;
  };

  return {
    ids: data.productIds,
    items: data.items,
    count: data.productIds.length,
    has: (productId: string) => data.productIds.includes(productId),
    toggle,
    remove: (productId: string) => removeMutation.mutate(productId),
    clear: () => clearMutation.mutate(),
    isLoading: query.isLoading,
    error: query.error,
  };
}
