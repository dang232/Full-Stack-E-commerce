/* eslint-disable react-refresh/only-export-components -- showNotificationToast is paired with its render component for Sonner custom toasts */
import { toast } from "sonner";

import type { Notification } from "../types/api/notification";
import { NotificationIcon } from "./notifications/notification-icon";

interface NotificationToastProps {
  notification: Notification;
  toastId: string | number;
  onNavigate: (path: string) => void;
}

export function NotificationToast({ notification, toastId, onNavigate }: NotificationToastProps) {
  const handleClick = () => {
    toast.dismiss(toastId);
    if (notification.deepLink) {
      onNavigate(notification.deepLink);
    }
  };

  return (
    <button
      onClick={handleClick}
      className="flex w-full max-w-sm gap-3 rounded-lg bg-card p-3 shadow-lg border border-border text-left hover:bg-muted transition-colors cursor-pointer"
    >
      <div className="mt-0.5 shrink-0">
        <NotificationIcon type={notification.type} size={18} />
      </div>
      <div className="min-w-0 flex-1">
        <p className="text-sm font-medium text-foreground truncate">{notification.title}</p>
        <p className="text-xs text-muted-foreground line-clamp-2 mt-0.5">{notification.body}</p>
      </div>
    </button>
  );
}

/**
 * Show a notification toast. Call this from the WebSocket hook when a new
 * notification arrives. Uses Sonner's custom toast with 5s auto-dismiss.
 */
export function showNotificationToast(
  notification: Notification,
  navigate: (path: string) => void,
): void {
  toast.custom(
    (id) => <NotificationToast notification={notification} toastId={id} onNavigate={navigate} />,
    { duration: 5000, position: "top-right" },
  );
}
