import { describe, expect, it, vi, beforeEach, afterEach } from "vitest";
import { renderHook, act, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { type ReactNode } from "react";

const useAuthMock = vi.fn();
vi.mock("./use-auth", () => ({
  useAuth: () => useAuthMock(),
}));

const getCartMock = vi.fn();
const addCartItemMock = vi.fn();
const updateCartItemMock = vi.fn();
const removeCartItemMock = vi.fn();
const clearCartMock = vi.fn();
vi.mock("../lib/api/endpoints/cart", () => ({
  getCart: (...args: unknown[]) => getCartMock(...args),
  addCartItem: (...args: unknown[]) => addCartItemMock(...args),
  updateCartItem: (...args: unknown[]) => updateCartItemMock(...args),
  removeCartItem: (...args: unknown[]) => removeCartItemMock(...args),
  clearCart: (...args: unknown[]) => clearCartMock(...args),
}));

import { useCart } from "./use-cart";

function makeWrapper() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  function Wrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
  }
  return { client, Wrapper };
}

beforeEach(() => {
  useAuthMock.mockReturnValue({ ready: true, authenticated: true });
  getCartMock.mockReset();
  addCartItemMock.mockReset();
  updateCartItemMock.mockReset();
  removeCartItemMock.mockReset();
  clearCartMock.mockReset();
});

afterEach(() => {
  vi.clearAllMocks();
});

describe("useCart", () => {
  it("does not fetch the cart while keycloak is initialising", () => {
    useAuthMock.mockReturnValue({ ready: false, authenticated: false });
    const { Wrapper } = makeWrapper();
    renderHook(() => useCart(), { wrapper: Wrapper });
    expect(getCartMock).not.toHaveBeenCalled();
  });

  it("does not fetch when unauthenticated", () => {
    useAuthMock.mockReturnValue({ ready: true, authenticated: false });
    const { Wrapper } = makeWrapper();
    renderHook(() => useCart(), { wrapper: Wrapper });
    expect(getCartMock).not.toHaveBeenCalled();
  });

  it("fetches the cart and exposes derived totals", async () => {
    getCartMock.mockResolvedValue({
      items: [
        { productId: "p1", price: 100, quantity: 2 },
        { productId: "p2", price: 50, quantity: 3 },
      ],
      totalAmount: 350,
      itemCount: 5,
    });
    const { Wrapper } = makeWrapper();
    const { result } = renderHook(() => useCart(), { wrapper: Wrapper });

    await waitFor(() => expect(result.current.isLoading).toBe(false));
    expect(result.current.items).toHaveLength(2);
    expect(result.current.itemCount).toBe(5);
    expect(result.current.totalAmount).toBe(350);
  });

  it("falls back to derived totals when the server omits them", async () => {
    getCartMock.mockResolvedValue({
      items: [
        { productId: "p1", price: 100, quantity: 2 },
        { productId: "p2", price: 50, quantity: 1 },
      ],
    });
    const { Wrapper } = makeWrapper();
    const { result } = renderHook(() => useCart(), { wrapper: Wrapper });

    await waitFor(() => expect(result.current.items).toHaveLength(2));
    expect(result.current.itemCount).toBe(3);
    expect(result.current.totalAmount).toBe(250);
  });

  it("addItem updates the cache directly on success without refetching", async () => {
    getCartMock.mockResolvedValue({ items: [], totalAmount: 0, itemCount: 0 });
    addCartItemMock.mockResolvedValue({
      items: [{ productId: "p1", price: 100, quantity: 1 }],
      itemCount: 1,
      totalAmount: 100,
    });
    const { Wrapper } = makeWrapper();
    const { result } = renderHook(() => useCart(), { wrapper: Wrapper });

    await waitFor(() => expect(result.current.items).toEqual([]));

    await act(async () => {
      await result.current.addItemAsync({ productId: "p1", quantity: 1 });
    });

    expect(addCartItemMock).toHaveBeenCalledWith({ productId: "p1", quantity: 1 });
    // Cart endpoint shouldn't be re-hit on success — cache was patched directly.
    expect(getCartMock).toHaveBeenCalledTimes(1);
    await waitFor(() => expect(result.current.itemCount).toBe(1));
    expect(result.current.totalAmount).toBe(100);
  });

  it("addItem rolls back to the previous cart when the mutation fails", async () => {
    const initialCart = { items: [], totalAmount: 0, itemCount: 0 };
    getCartMock.mockResolvedValue(initialCart);
    addCartItemMock.mockRejectedValueOnce(new Error("server boom"));
    const { Wrapper } = makeWrapper();
    const { result } = renderHook(() => useCart(), { wrapper: Wrapper });

    await waitFor(() => expect(result.current.items).toEqual([]));

    await act(async () => {
      await result.current
        .addItemAsync({ productId: "p1", quantity: 1 })
        .catch(() => undefined);
    });

    // Cache restored from snapshot — no extra refetch needed.
    expect(getCartMock).toHaveBeenCalledTimes(1);
    await waitFor(() => expect(result.current.items).toEqual([]));
    expect(result.current.itemCount).toBe(0);
  });

  it("updateItem and removeItem patch the cache from the server response", async () => {
    getCartMock.mockResolvedValue({
      items: [{ productId: "p1", price: 100, quantity: 2 }],
      totalAmount: 200,
      itemCount: 2,
    });
    updateCartItemMock.mockResolvedValue({
      items: [{ productId: "p1", price: 100, quantity: 5 }],
      totalAmount: 500,
      itemCount: 5,
    });
    removeCartItemMock.mockResolvedValue({ items: [], totalAmount: 0, itemCount: 0 });

    const { Wrapper } = makeWrapper();
    const { result } = renderHook(() => useCart(), { wrapper: Wrapper });

    await waitFor(() => expect(result.current.itemCount).toBe(2));

    await act(async () => {
      result.current.updateItem({ productId: "p1", quantity: 5 });
    });
    await waitFor(() => expect(result.current.itemCount).toBe(5));
    expect(updateCartItemMock).toHaveBeenCalledWith("p1", { quantity: 5 });

    await act(async () => {
      result.current.removeItem("p1");
    });
    await waitFor(() => expect(result.current.itemCount).toBe(0));
    expect(removeCartItemMock).toHaveBeenCalledWith("p1");
  });

  it("clear sets the cart to an empty shape locally", async () => {
    getCartMock.mockResolvedValue({
      items: [{ productId: "p1", price: 100, quantity: 1 }],
      totalAmount: 100,
      itemCount: 1,
    });
    clearCartMock.mockResolvedValue({});

    const { Wrapper } = makeWrapper();
    const { result } = renderHook(() => useCart(), { wrapper: Wrapper });

    await waitFor(() => expect(result.current.itemCount).toBe(1));

    await act(async () => {
      result.current.clear();
    });

    await waitFor(() => expect(result.current.items).toEqual([]));
    expect(clearCartMock).toHaveBeenCalledTimes(1);
  });
});
