import { Notification } from '../domain/notification';
import { NotificationStatus } from '../domain/notification-status.enum';
import { NotificationType } from '../domain/notification-type.enum';
import { NotificationRepository } from '../domain/notification.repository';

export const TEST_NOTIFICATION_TYPE = NotificationType.ORDER_CREATED;

export interface NotificationChannel {
  name: string;
  send(notification: Notification): Promise<void>;
}

export interface SendNotificationInput {
  type: NotificationType;
  userId: string;
  title: string;
  body: string;
  data?: Record<string, unknown>;
  channels?: string[];
}

export class SendNotificationUseCase {
  constructor(
    private readonly repository: NotificationRepository,
    private readonly channelAdapters: NotificationChannel[] = [],
  ) {}

  async send(input: SendNotificationInput): Promise<Notification> {
    const notification = Notification.create({
      userId: input.userId,
      type: input.type,
      title: input.title,
      body: input.body,
      data: input.data ?? {},
      channels:
        input.channels ?? this.channelAdapters.map((channel) => channel.name),
    });

    const savedNotification = await this.repository.save(notification);

    for (const channel of this.channelAdapters) {
      if (savedNotification.channels.includes(channel.name)) {
        await channel.send(savedNotification);
      }
    }

    await this.repository.markSent(savedNotification.id);
    savedNotification.status = NotificationStatus.SENT;

    return savedNotification;
  }
}
