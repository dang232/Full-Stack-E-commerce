import { z } from "zod";

export const notificationSchema = z
  .object({
    id: z.string(),
    type: z.string().optional(),
    title: z.string().optional(),
    body: z.string().optional(),
    read: z.boolean().optional(),
    createdAt: z.string().optional(),
    deepLink: z.string().nullable().optional(),
  })
  .passthrough();
export type Notification = z.infer<typeof notificationSchema>;

/** Response from `POST /notifications/mark-all-read`. */
export const markAllNotificationsReadResponseSchema = z
  .object({ updated: z.number() })
  .passthrough();

/** Response from `GET /notifications/unread-count`. */
export const unreadNotificationCountResponseSchema = z.object({ count: z.number() }).passthrough();
