import { Inject, Injectable, Logger } from '@nestjs/common';
import { OnEvent } from '@nestjs/event-emitter';
import { NotificationCreatedEvent } from '../../domain/event/notification-created.event';
import { NotificationChannel } from '../../domain/model/notification-preferences';
import {
  NOTIFICATION_REPOSITORY,
  NotificationRepository,
} from '../../domain/port/outbound/notification.repository';
import {
  NOTIFICATION_PREFERENCES_REPOSITORY,
  NotificationPreferencesRepository,
} from '../../domain/port/outbound/notification-preferences.repository';
import {
  REALTIME_CHANNEL_PORT,
  RealtimeChannelPort,
} from '../../domain/port/outbound/realtime-channel.port';
import {
  CONNECTION_REGISTRY_PORT,
  ConnectionRegistryPort,
} from '../../domain/port/outbound/connection-registry.port';

@Injectable()
export class NotificationCreatedHandler {
  private readonly logger = new Logger(NotificationCreatedHandler.name);

  constructor(
    @Inject(NOTIFICATION_REPOSITORY) private readonly repo: NotificationRepository,
    @Inject(NOTIFICATION_PREFERENCES_REPOSITORY)
    private readonly prefsRepo: NotificationPreferencesRepository,
    @Inject(REALTIME_CHANNEL_PORT) private readonly channel: RealtimeChannelPort,
    @Inject(CONNECTION_REGISTRY_PORT) private readonly registry: ConnectionRegistryPort,
  ) {}

  @OnEvent('notification.created')
  async handle(event: NotificationCreatedEvent): Promise<void> {
    const notification = await this.repo.findById(event.notificationId);
    if (!notification) return;

    const prefs = await this.prefsRepo.findByUserId(event.userId);
    const inAppEnabled = prefs
      ? prefs.isChannelEnabled(notification.type, NotificationChannel.IN_APP)
      : true; // default to enabled if no preferences exist

    if (!inAppEnabled) {
      this.logger.debug(
        `IN_APP channel disabled for user ${event.userId}, type ${notification.type} — skipping WebSocket delivery`,
      );
      return;
    }

    const isOnline = await this.registry.isOnline(event.userId);

    if (isOnline) {
      try {
        notification.markSent();
        await this.channel.sendToUser(event.userId, notification);
        // Don't mark DELIVERED here — wait for client ACK (future enhancement)
        await this.repo.save(notification);
        this.logger.debug(`Sent notification ${notification.id} to ${event.userId}`);
      } catch (err) {
        notification.markFailed();
        await this.repo.save(notification);
        await this.registry.enqueueOffline(event.userId, notification.id);
        this.logger.warn(`Delivery failed for ${notification.id}: ${err}`);
      }
    } else {
      notification.markSent();
      await this.repo.save(notification);
      await this.registry.enqueueOffline(event.userId, notification.id);
      this.logger.debug(`User ${event.userId} offline — queued ${notification.id}`);
    }
  }
}
