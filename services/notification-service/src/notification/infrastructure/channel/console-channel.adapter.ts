import { Injectable, Logger } from '@nestjs/common';
import { NotificationChannel } from '../../application/send-notification.use-case';
import { Notification } from '../../domain/notification';

@Injectable()
export class ConsoleChannelAdapter implements NotificationChannel {
  readonly name = 'console';
  private readonly logger = new Logger(ConsoleChannelAdapter.name);

  async send(notification: Notification): Promise<void> {
    this.logger.log(`${notification.type} notification for ${notification.userId}: ${notification.title}`);
  }
}
