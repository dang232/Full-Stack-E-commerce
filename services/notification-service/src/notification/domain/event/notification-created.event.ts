import { NotificationType } from '../model/notification-type.enum';
import { DomainEvent } from './domain-event';

export class NotificationCreatedEvent implements DomainEvent {
  readonly occurredAt: Date;

  constructor(
    readonly notificationId: string,
    readonly userId: string,
    readonly type: NotificationType,
  ) {
    this.occurredAt = new Date();
  }
}
