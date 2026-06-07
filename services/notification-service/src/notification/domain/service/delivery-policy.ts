import { NotificationType } from '../model/notification-type.enum';
import { Priority } from '../model/priority.enum';

export interface DeliveryPolicy {
  shouldDeliverRealtime(type: NotificationType): boolean;
  getRetryDelayMs(attemptNumber: number): number;
  getMaxRetries(priority: Priority): number;
}

export class DefaultDeliveryPolicy implements DeliveryPolicy {
  private static readonly ALWAYS_REALTIME = new Set<NotificationType>([
    NotificationType.ORDER_CREATED,
    NotificationType.ORDER_SHIPPED,
    NotificationType.ORDER_DELIVERED,
    NotificationType.ORDER_CANCELLED,
    NotificationType.PAYMENT_COMPLETED,
    NotificationType.PAYMENT_REFUNDED,
    NotificationType.SELLER_NEW_ORDER,
    NotificationType.RETURN_REQUESTED,
  ]);

  shouldDeliverRealtime(type: NotificationType): boolean {
    return DefaultDeliveryPolicy.ALWAYS_REALTIME.has(type);
  }

  getRetryDelayMs(attemptNumber: number): number {
    // Exponential backoff: 1s, 2s, 4s (capped at 5min)
    return Math.min(1000 * Math.pow(2, attemptNumber), 300_000);
  }

  getMaxRetries(priority: Priority): number {
    switch (priority) {
      case Priority.HIGH:
        return 3;
      case Priority.MEDIUM:
        return 2;
      case Priority.LOW:
        return 1;
    }
  }
}
