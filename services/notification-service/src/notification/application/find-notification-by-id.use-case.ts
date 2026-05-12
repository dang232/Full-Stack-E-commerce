import { Notification } from '../domain/notification';
import { NotificationRepository } from '../domain/notification.repository';

export class FindNotificationByIdUseCase {
  constructor(private readonly repository: NotificationRepository) {}

  async execute(id: string): Promise<Notification | null> {
    return this.repository.findById(id);
  }
}
