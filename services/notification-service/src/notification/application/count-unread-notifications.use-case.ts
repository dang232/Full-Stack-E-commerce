import { NotificationRepository } from '../domain/notification.repository';

export class CountUnreadNotificationsUseCase {
  constructor(private readonly repository: NotificationRepository) {}

  async execute(userId: string): Promise<number> {
    return this.repository.countUnread(userId);
  }
}
