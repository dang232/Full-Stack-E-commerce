import { Inject, Injectable } from '@nestjs/common';
import {
  NOTIFICATION_REPOSITORY,
  NotificationRepository,
  ThreadSummary,
} from '../../domain/port/outbound/notification.repository';
import { NotificationType } from '../../domain/model/notification-type.enum';

export interface FindThreadsQuery {
  userId: string;
  type?: NotificationType;
  page?: number;
  limit?: number;
}

export interface PaginatedThreads {
  threads: ThreadSummary[];
  total: number;
  page: number;
  limit: number;
  totalPages: number;
}

@Injectable()
export class FindNotificationThreadsUseCase {
  /* istanbul ignore next */
  constructor(
    @Inject(NOTIFICATION_REPOSITORY)
    private readonly repo: NotificationRepository,
  ) {}

  async execute(query: FindThreadsQuery): Promise<PaginatedThreads> {
    const page = query.page ?? 0;
    const limit = Math.min(query.limit ?? 20, 50);

    const { threads, total } = await this.repo.findThreadsByUser(
      query.userId,
      page,
      limit,
      query.type,
    );

    const totalPages = Math.ceil(total / limit);
    return { threads, total, page, limit, totalPages };
  }
}
