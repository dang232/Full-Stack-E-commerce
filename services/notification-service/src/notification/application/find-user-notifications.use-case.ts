import { Notification } from '../domain/notification';
import {
  NotificationRepository,
  PageResult,
} from '../domain/notification.repository';

export class FindUserNotificationsUseCase {
  constructor(private readonly repository: NotificationRepository) {}

  async execute(userId: string): Promise<Notification[]> {
    return this.repository.findByUserId(userId);
  }

  async executePaged(
    userId: string,
    page: number,
    size: number,
  ): Promise<PageResult<Notification>> {
    const safePage = Number.isFinite(page) && page >= 0 ? Math.floor(page) : 0;
    const safeSize =
      Number.isFinite(size) && size > 0 ? Math.min(Math.floor(size), 100) : 30;
    return this.repository.findByUserIdPaged(userId, {
      page: safePage,
      size: safeSize,
    });
  }
}
