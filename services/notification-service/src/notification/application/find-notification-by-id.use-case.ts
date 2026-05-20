import { NotFoundException } from '@nestjs/common';
import { Notification } from '../domain/notification';
import { NotificationRepository } from '../domain/notification.repository';

export class FindNotificationByIdUseCase {
  constructor(private readonly repository: NotificationRepository) {}

  async execute(id: string, userId: string): Promise<Notification> {
    const notification = await this.repository.findByIdAndUserId(id, userId);
    if (!notification) {
      throw new NotFoundException('Notification not found');
    }
    return notification;
  }
}
