import { useEffect } from "react";
import { create } from "zustand";

interface WishlistState {
  ids: string[];
  hydrated: boolean;
  hydrate: () => void;
  toggle: (id: string) => boolean;
  has: (id: string) => boolean;
  clear: () => void;
}

const STORAGE_KEY = "vnshop:wishlist";

export const useWishlistStore = create<WishlistState>((set, get) => ({
  ids: [],
  hydrated: false,
  hydrate: () => {
    if (get().hydrated) return;
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      const parsed = raw ? (JSON.parse(raw) as unknown) : null;
      if (Array.isArray(parsed) && parsed.every((x) => typeof x === "string")) {
        set({ ids: parsed, hydrated: true });
        return;
      }
    } catch {
      /* fall through to empty */
    }
    set({ hydrated: true });
  },
  toggle: (id) => {
    const present = get().ids.includes(id);
    const next = present ? get().ids.filter((x) => x !== id) : [...get().ids, id];
    set({ ids: next });
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
    } catch {
      /* storage full / disabled — ignore */
    }
    return !present;
  },
  has: (id) => get().ids.includes(id),
  clear: () => {
    set({ ids: [] });
    try {
      localStorage.removeItem(STORAGE_KEY);
    } catch {
      /* ignore */
    }
  },
}));

export function useWishlist() {
  const ids = useWishlistStore((s) => s.ids);
  const hydrate = useWishlistStore((s) => s.hydrate);
  const toggle = useWishlistStore((s) => s.toggle);
  const has = useWishlistStore((s) => s.has);
  const clear = useWishlistStore((s) => s.clear);

  useEffect(() => {
    hydrate();
  }, [hydrate]);

  return { ids, toggle, has, clear, count: ids.length };
}
