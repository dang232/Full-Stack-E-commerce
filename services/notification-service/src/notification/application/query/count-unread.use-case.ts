import { Inject, Injectable } from '@nestjs/common';
import {
  NOTIFICATION_REPOSITORY,
  NotificationRepository,
} from '../../domain/port/outbound/notification.repository';

@Injectable()
export class CountUnreadUseCase {
  /* istanbul ignore next */
  constructor(
    @Inject(NOTIFICATION_REPOSITORY)
    private readonly repo: NotificationRepository,
  ) {}

  async execute(userId: string): Promise<number> {
    return this.repo.countUnread(userId);
  }
}
