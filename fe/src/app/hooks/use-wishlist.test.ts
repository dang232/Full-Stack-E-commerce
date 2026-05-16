import { afterEach, beforeEach, describe, expect, it } from "vitest";

import { useWishlistStore } from "./use-wishlist";

describe("useWishlistStore", () => {
  beforeEach(() => {
    localStorage.clear();
    useWishlistStore.setState({ ids: [], hydrated: false });
  });
  afterEach(() => {
    localStorage.clear();
  });

  it("toggles items into and out of the wishlist and persists to localStorage", () => {
    const { toggle, has } = useWishlistStore.getState();

    expect(has("p1")).toBe(false);
    const addedFirst = toggle("p1");
    expect(addedFirst).toBe(true);
    expect(useWishlistStore.getState().ids).toEqual(["p1"]);
    expect(JSON.parse(localStorage.getItem("vnshop:wishlist") ?? "[]")).toEqual(["p1"]);

    const addedSecond = toggle("p2");
    expect(addedSecond).toBe(true);
    expect(useWishlistStore.getState().ids).toEqual(["p1", "p2"]);

    const removedFirst = toggle("p1");
    expect(removedFirst).toBe(false);
    expect(useWishlistStore.getState().ids).toEqual(["p2"]);
    expect(JSON.parse(localStorage.getItem("vnshop:wishlist") ?? "[]")).toEqual(["p2"]);
  });

  it("clear() empties both store and localStorage", () => {
    const { toggle, clear } = useWishlistStore.getState();
    toggle("a");
    toggle("b");
    expect(useWishlistStore.getState().ids).toEqual(["a", "b"]);
    clear();
    expect(useWishlistStore.getState().ids).toEqual([]);
    expect(localStorage.getItem("vnshop:wishlist")).toBeNull();
  });

  it("hydrate() reads from localStorage exactly once", () => {
    localStorage.setItem("vnshop:wishlist", JSON.stringify(["x", "y"]));
    const { hydrate } = useWishlistStore.getState();
    hydrate();
    expect(useWishlistStore.getState().ids).toEqual(["x", "y"]);
    expect(useWishlistStore.getState().hydrated).toBe(true);

    // Subsequent calls should be no-ops even if storage changes.
    localStorage.setItem("vnshop:wishlist", JSON.stringify(["z"]));
    hydrate();
    expect(useWishlistStore.getState().ids).toEqual(["x", "y"]);
  });

  it("hydrate() recovers gracefully from corrupt localStorage values", () => {
    localStorage.setItem("vnshop:wishlist", "not-json");
    useWishlistStore.getState().hydrate();
    expect(useWishlistStore.getState().ids).toEqual([]);
    expect(useWishlistStore.getState().hydrated).toBe(true);
  });

  it("hydrate() ignores localStorage values that aren't string arrays", () => {
    localStorage.setItem("vnshop:wishlist", JSON.stringify({ a: 1 }));
    useWishlistStore.getState().hydrate();
    expect(useWishlistStore.getState().ids).toEqual([]);
  });
});
