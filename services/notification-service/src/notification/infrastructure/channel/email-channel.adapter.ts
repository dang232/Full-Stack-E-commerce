import { Injectable } from '@nestjs/common';
import { NotificationChannel } from '../../application/send-notification.use-case';
import { Notification } from '../../domain/notification';

@Injectable()
export class EmailChannelAdapter implements NotificationChannel {
  readonly name = 'email';

  send(notification: Notification): Promise<void> {
    void notification;
    return Promise.resolve();
  }
}
