import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";

import { getNotification, listNotifications } from "../lib/api/endpoints/notifications";
import type { Notification } from "../types/api";

import { useAuth } from "./use-auth";

const POLL_INTERVAL_MS = 30_000;
const NOTIFICATIONS_KEY = ["notifications", "list"] as const;

/** Tracks whether the document is currently visible. Used to gate background polling. */
function usePageVisible() {
  const [visible, setVisible] = useState(typeof document === "undefined" ? true : !document.hidden);
  useEffect(() => {
    const onChange = () => setVisible(!document.hidden);
    document.addEventListener("visibilitychange", onChange);
    return () => document.removeEventListener("visibilitychange", onChange);
  }, []);
  return visible;
}

export function useNotifications() {
  const { ready, authenticated } = useAuth();
  const visible = usePageVisible();
  const qc = useQueryClient();

  const query = useQuery<Notification[]>({
    queryKey: NOTIFICATIONS_KEY,
    queryFn: () => listNotifications({ size: 30 }),
    enabled: ready && authenticated,
    refetchInterval: visible ? POLL_INTERVAL_MS : false,
    refetchOnWindowFocus: true,
    retry: false,
  });

  /**
   * Optimistically mark a notification as read, then call the server. The detail-fetch
   * endpoint is what actually flips `read: true` server-side. On failure we roll back
   * so the bell count stays accurate.
   */
  const markRead = useMutation<Notification, unknown, string, { previous?: Notification[] }>({
    mutationFn: (id: string) => getNotification(id),
    onMutate: async (id) => {
      await qc.cancelQueries({ queryKey: NOTIFICATIONS_KEY });
      const previous = qc.getQueryData<Notification[]>(NOTIFICATIONS_KEY);
      qc.setQueryData<Notification[]>(
        NOTIFICATIONS_KEY,
        (prev) => prev?.map((n) => (n.id === id ? { ...n, read: true } : n)) ?? prev,
      );
      return { previous };
    },
    onSuccess: (updated, id) => {
      // Reconcile with whatever the server returned (in case more fields changed).
      qc.setQueryData<Notification[]>(
        NOTIFICATIONS_KEY,
        (prev) => prev?.map((n) => (n.id === id ? { ...n, read: true, ...updated } : n)) ?? prev,
      );
    },
    onError: (_err, _id, context) => {
      if (context?.previous) qc.setQueryData(NOTIFICATIONS_KEY, context.previous);
    },
  });

  const items = query.data ?? [];
  const unreadCount = items.filter((n) => n.read === false).length;

  return {
    items,
    unreadCount,
    isLoading: query.isLoading,
    error: query.error,
    refetch: query.refetch,
    markRead: markRead.mutate,
  };
}
