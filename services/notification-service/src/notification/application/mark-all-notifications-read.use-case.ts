import { NotificationRepository } from '../domain/notification.repository';

export class MarkAllNotificationsReadUseCase {
  constructor(private readonly repository: NotificationRepository) {}

  /** Marks every unread notification for the given user as read. Returns the affected count. */
  async execute(userId: string): Promise<number> {
    return this.repository.markAllRead(userId);
  }
}
