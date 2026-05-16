import { Notification } from '../domain/notification';
import { NotificationRepository } from '../domain/notification.repository';

export class MarkNotificationReadUseCase {
  constructor(private readonly repository: NotificationRepository) {}

  /**
   * Marks a single notification as read for the given user. Returns null when
   * the notification does not exist or belongs to a different user — the
   * controller layer translates that into a 404.
   */
  async execute(id: string, userId: string): Promise<Notification | null> {
    return this.repository.markRead(id, userId);
  }
}
