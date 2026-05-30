import { Inject, Injectable } from '@nestjs/common';
import {
  NOTIFICATION_REPOSITORY,
  NotificationRepository,
  FindUserNotificationsOptions,
} from '../../domain/port/outbound/notification.repository';
import { Notification } from '../../domain/model/notification';
import { NotificationType } from '../../domain/model/notification-type.enum';

export interface FindUserNotificationsQuery {
  userId: string;
  type?: NotificationType;
  threadId?: string;
  page?: number;
  limit?: number;
}

export interface PaginatedNotifications {
  items: Notification[];
  total: number;
  page: number;
  limit: number;
  totalPages: number;
}

@Injectable()
export class FindUserNotificationsUseCase {
  constructor(
    @Inject(NOTIFICATION_REPOSITORY) private readonly repo: NotificationRepository,
  ) {}

  async execute(query: FindUserNotificationsQuery): Promise<PaginatedNotifications> {
    const page = query.page ?? 0;
    const limit = Math.min(query.limit ?? 20, 100);

    const options: FindUserNotificationsOptions = {
      userId: query.userId,
      type: query.type,
      threadId: query.threadId,
      page,
      limit,
    };

    const { items, total } = await this.repo.findByUser(options);
    const totalPages = Math.ceil(total / limit);

    return { items, total, page, limit, totalPages };
  }
}
