import { z } from "zod";

import { notificationSchema, pageSchema } from "../../../types/api";
import { api } from "../client";

export const listNotifications = (params: { page?: number; size?: number } = {}) =>
  api.get("/notifications", pageSchema(notificationSchema), {
    page: params.page,
    size: params.size ?? 30,
  });

export const getNotification = (id: string) =>
  api.get(`/notifications/${encodeURIComponent(id)}`, notificationSchema);

export const markNotificationRead = (id: string) =>
  api.post(`/notifications/${encodeURIComponent(id)}/read`, notificationSchema);

const markAllReadResponseSchema = z.object({ updated: z.number() }).passthrough();
export const markAllNotificationsRead = () =>
  api.post("/notifications/mark-all-read", markAllReadResponseSchema);

const unreadCountResponseSchema = z.object({ count: z.number() }).passthrough();
export const unreadNotificationCount = () =>
  api.get("/notifications/unread-count", unreadCountResponseSchema);
