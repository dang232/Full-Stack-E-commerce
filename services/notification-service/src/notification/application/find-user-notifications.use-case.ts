import { Notification } from '../domain/notification';
import { NotificationRepository } from '../domain/notification.repository';

export class FindUserNotificationsUseCase {
  constructor(private readonly repository: NotificationRepository) {}

  async execute(userId: string): Promise<Notification[]> {
    return this.repository.findByUserId(userId);
  }
}
