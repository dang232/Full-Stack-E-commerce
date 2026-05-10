import { Injectable } from '@nestjs/common';
import { NotificationChannel } from '../../application/send-notification.use-case';
import { Notification } from '../../domain/notification';

@Injectable()
export class EmailChannelAdapter implements NotificationChannel {
  readonly name = 'email';

  async send(_notification: Notification): Promise<void> {
    // TODO: connect real email provider.
  }
}
