import { z } from "zod";

import { notificationSchema } from "../../../types/api";
import { api } from "../client";

export const listNotifications = (params: { page?: number; size?: number } = {}) =>
  api.get("/notifications", z.array(notificationSchema), {
    page: params.page,
    size: params.size ?? 30,
  });

export const getNotification = (id: string) =>
  api.get(`/notifications/${encodeURIComponent(id)}`, notificationSchema);
