import { Inject, Injectable } from '@nestjs/common';
import {
  NOTIFICATION_REPOSITORY,
  NotificationRepository,
} from '../../domain/port/outbound/notification.repository';

@Injectable()
export class MarkAllReadUseCase {
  /* istanbul ignore next */
  constructor(
    @Inject(NOTIFICATION_REPOSITORY) /* istanbul ignore next */
    private readonly repo: NotificationRepository,
  ) {}

  async execute(userId: string): Promise<number> {
    return this.repo.markAllReadForUser(userId);
  }
}
