import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";

import { listThreads, type MessageThreadSummary } from "../lib/api/endpoints/messaging";

import { useAuth } from "./use-auth";

const POLL_INTERVAL_MS = 30_000;
export const THREADS_KEY = ["messaging", "threads"] as const;

function usePageVisible() {
  const [visible, setVisible] = useState(typeof document === "undefined" ? true : !document.hidden);
  useEffect(() => {
    const onChange = () => setVisible(!document.hidden);
    document.addEventListener("visibilitychange", onChange);
    return () => document.removeEventListener("visibilitychange", onChange);
  }, []);
  return visible;
}

/** Lists the caller's threads. The WebSocket pushes message arrivals; this
 * polls only as a safety net for missed events / first paint. */
export function useThreads(limit = 50) {
  const { ready, authenticated } = useAuth();
  const visible = usePageVisible();

  const query = useQuery({
    queryKey: THREADS_KEY,
    queryFn: () => listThreads({ limit }),
    enabled: ready && authenticated,
    refetchInterval: visible ? POLL_INTERVAL_MS : false,
    refetchOnWindowFocus: true,
    retry: false,
  });

  const items: MessageThreadSummary[] = query.data?.content ?? [];
  const totalUnread = items.reduce((sum, t) => sum + (t.unreadCount ?? 0), 0);

  return {
    items,
    totalUnread,
    isLoading: query.isLoading,
    error: query.error,
    refetch: query.refetch,
  };
}

/** Helper for components that need to bump the thread cache after a side effect
 * (e.g. WebSocket push, message sent). */
export function useInvalidateThreads() {
  const qc = useQueryClient();
  return () => qc.invalidateQueries({ queryKey: THREADS_KEY });
}
