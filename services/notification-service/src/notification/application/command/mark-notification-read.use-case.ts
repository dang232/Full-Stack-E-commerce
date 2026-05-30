import { Inject, Injectable, NotFoundException } from '@nestjs/common';
import { Notification } from '../../domain/model/notification';
import {
  NOTIFICATION_REPOSITORY,
  NotificationRepository,
} from '../../domain/port/outbound/notification.repository';

@Injectable()
export class MarkNotificationReadUseCase {
  constructor(
    @Inject(NOTIFICATION_REPOSITORY) private readonly repo: NotificationRepository,
  ) {}

  async execute(notificationId: string, userId: string): Promise<Notification> {
    const notification = await this.repo.findByIdAndUserId(notificationId, userId);
    if (!notification) {
      throw new NotFoundException(`Notification ${notificationId} not found`);
    }

    notification.markRead();
    await this.repo.save(notification);
    return notification;
  }
}
