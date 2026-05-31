import { Inject, Injectable } from '@nestjs/common';
import {
  NOTIFICATION_REPOSITORY,
  NotificationRepository,
} from '../../domain/port/outbound/notification.repository';
import { Notification } from '../../domain/model/notification';

@Injectable()
export class FindThreadNotificationsUseCase {
  constructor(
    @Inject(NOTIFICATION_REPOSITORY) private readonly repo: NotificationRepository,
  ) {}

  async execute(threadId: string, userId: string): Promise<Notification[]> {
    return this.repo.findByThread(threadId, userId);
  }
}
