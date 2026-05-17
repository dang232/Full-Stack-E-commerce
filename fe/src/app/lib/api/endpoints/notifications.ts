import {
  markAllNotificationsReadResponseSchema,
  notificationSchema,
  pageSchema,
  unreadNotificationCountResponseSchema,
} from "../../../types/api";
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

export const markAllNotificationsRead = () =>
  api.post("/notifications/mark-all-read", markAllNotificationsReadResponseSchema);

export const unreadNotificationCount = () =>
  api.get("/notifications/unread-count", unreadNotificationCountResponseSchema);
