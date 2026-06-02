import { NotificationChannel } from '../model/notification-preferences';
import { NotificationType } from '../model/notification-type.enum';
import { DomainEvent } from './domain-event';

export class NotificationCreatedEvent implements DomainEvent {
  readonly occurredAt: Date;
  readonly suppressedChannels: NotificationChannel[];

  constructor(
    readonly notificationId: string,
    readonly userId: string,
    readonly type: NotificationType,
    suppressedChannels?: NotificationChannel[],
  ) {
    this.occurredAt = new Date();
    this.suppressedChannels = suppressedChannels ?? [];
  }
}
