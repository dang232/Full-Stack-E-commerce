import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor, act } from "@testing-library/react";
import { type ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const useAuthMock = vi.fn();
vi.mock("./use-auth", () => ({
  useAuth: () => useAuthMock(),
}));

const listNotificationsMock = vi.fn();
const getNotificationMock = vi.fn();
vi.mock("../lib/api/endpoints/notifications", () => ({
  listNotifications: (...args: unknown[]) => listNotificationsMock(...args),
  getNotification: (...args: unknown[]) => getNotificationMock(...args),
}));

import { useNotifications } from "./use-notifications";

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
  listNotificationsMock.mockReset();
  getNotificationMock.mockReset();
});

afterEach(() => {
  vi.clearAllMocks();
});

describe("useNotifications", () => {
  it("does not fetch when keycloak isn't ready", () => {
    useAuthMock.mockReturnValue({ ready: false, authenticated: false });
    const { Wrapper } = makeWrapper();
    renderHook(() => useNotifications(), { wrapper: Wrapper });
    expect(listNotificationsMock).not.toHaveBeenCalled();
  });

  it("does not fetch when unauthenticated", () => {
    useAuthMock.mockReturnValue({ ready: true, authenticated: false });
    const { Wrapper } = makeWrapper();
    renderHook(() => useNotifications(), { wrapper: Wrapper });
    expect(listNotificationsMock).not.toHaveBeenCalled();
  });

  it("returns items and computes unreadCount from server data", async () => {
    listNotificationsMock.mockResolvedValue([
      { id: "n1", title: "Đơn hàng đã giao", read: false },
      { id: "n2", title: "Khuyến mãi", read: true },
      { id: "n3", title: "Tin mới", read: false },
    ]);
    const { Wrapper } = makeWrapper();
    const { result } = renderHook(() => useNotifications(), { wrapper: Wrapper });

    await waitFor(() => expect(result.current.items).toHaveLength(3));
    expect(result.current.unreadCount).toBe(2);
  });

  it("counts entries without an explicit `read` field as already read (no false positives)", async () => {
    listNotificationsMock.mockResolvedValue([
      { id: "n1", title: "Tin 1" },
      { id: "n2", title: "Tin 2", read: false },
    ]);
    const { Wrapper } = makeWrapper();
    const { result } = renderHook(() => useNotifications(), { wrapper: Wrapper });

    await waitFor(() => expect(result.current.items).toHaveLength(2));
    expect(result.current.unreadCount).toBe(1);
  });

  it("markRead patches the cache without an extra list refetch", async () => {
    listNotificationsMock.mockResolvedValue([
      { id: "n1", title: "Một", read: false },
      { id: "n2", title: "Hai", read: false },
    ]);
    getNotificationMock.mockResolvedValue({ id: "n1", title: "Một", read: true });

    const { Wrapper } = makeWrapper();
    const { result } = renderHook(() => useNotifications(), { wrapper: Wrapper });

    await waitFor(() => expect(result.current.items).toHaveLength(2));
    expect(result.current.unreadCount).toBe(2);

    await act(async () => {
      result.current.markRead("n1");
    });

    await waitFor(() => expect(result.current.unreadCount).toBe(1));
    expect(getNotificationMock).toHaveBeenCalledWith("n1");
    // List endpoint should still have been called only once — the patch is local.
    expect(listNotificationsMock).toHaveBeenCalledTimes(1);
  });

  it("exposes loading state while the initial fetch is in flight", () => {
    listNotificationsMock.mockImplementation(() => new Promise(() => undefined));
    const { Wrapper } = makeWrapper();
    const { result } = renderHook(() => useNotifications(), { wrapper: Wrapper });
    expect(result.current.isLoading).toBe(true);
    expect(result.current.items).toEqual([]);
    expect(result.current.unreadCount).toBe(0);
  });

  it("propagates errors from the server", async () => {
    listNotificationsMock.mockRejectedValue(new Error("boom"));
    const { Wrapper } = makeWrapper();
    const { result } = renderHook(() => useNotifications(), { wrapper: Wrapper });
    await waitFor(() => expect(result.current.error).toBeInstanceOf(Error));
  });
});
