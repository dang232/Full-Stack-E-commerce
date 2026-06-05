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
import {
  EMAIL_CHANNEL_PORT,
  EmailChannelPort,
} from '../../domain/port/outbound/email-channel.port';
import {
  PUSH_CHANNEL_PORT,
  PushChannelPort,
} from '../../domain/port/outbound/push-channel.port';
import { Notification } from '../../domain/model/notification';

@Injectable()
export class NotificationCreatedHandler {
  private readonly logger = new Logger(NotificationCreatedHandler.name);

  constructor(
    @Inject(NOTIFICATION_REPOSITORY) private readonly repo: NotificationRepository,
    @Inject(NOTIFICATION_PREFERENCES_REPOSITORY)
    private readonly prefsRepo: NotificationPreferencesRepository,
    @Inject(REALTIME_CHANNEL_PORT) private readonly realtimeChannel: RealtimeChannelPort,
    @Inject(CONNECTION_REGISTRY_PORT) private readonly registry: ConnectionRegistryPort,
    @Inject(EMAIL_CHANNEL_PORT) private readonly emailChannel: EmailChannelPort,
    @Inject(PUSH_CHANNEL_PORT) private readonly pushChannel: PushChannelPort,
  ) {}

  @OnEvent('notification.created')
  async handle(event: NotificationCreatedEvent): Promise<void> {
    const notification = await this.repo.findById(event.notificationId);
    if (!notification) return;

    const prefs = await this.prefsRepo.findByUserId(event.userId);

    const isChannelActive = (channel: NotificationChannel): boolean => {
      if (event.suppressedChannels.includes(channel)) return false;
      if (!prefs) return true;
      return prefs.isChannelEnabled(notification.type, channel);
    };

    await Promise.all([
      this.dispatchInApp(event, notification, isChannelActive(NotificationChannel.IN_APP)),
      this.dispatchEmail(event, notification, isChannelActive(NotificationChannel.EMAIL)),
      this.dispatchPush(event, notification, isChannelActive(NotificationChannel.PUSH)),
    ]);
  }

  private async dispatchInApp(
    event: NotificationCreatedEvent,
    notification: Notification,
    enabled: boolean,
  ): Promise<void> {
    if (!enabled) {
      this.logger.debug(
        `IN_APP channel disabled for user ${event.userId}, type ${notification.type} — skipping WebSocket delivery`,
      );
      return;
    }

    const isOnline = await this.registry.isOnline(event.userId);

    if (isOnline) {
      try {
        notification.markSent();
        await this.realtimeChannel.sendToUser(event.userId, notification);
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

  private async dispatchEmail(
    event: NotificationCreatedEvent,
    notification: Notification,
    enabled: boolean,
  ): Promise<void> {
    if (!enabled || !event.recipientEmail) return;
    if (!this.emailChannel.isEnabled()) return;

    try {
      await this.emailChannel.send(
        { userId: event.userId, email: event.recipientEmail },
        notification,
      );
    } catch (error) {
      this.logger.error(`Email dispatch failed for notification ${notification.id}`, error);
    }
  }

  private async dispatchPush(
    event: NotificationCreatedEvent,
    notification: Notification,
    enabled: boolean,
  ): Promise<void> {
    if (!enabled || !event.recipientDeviceToken) return;
    if (!this.pushChannel.isEnabled()) return;

    try {
      await this.pushChannel.send(
        { userId: event.userId, deviceToken: event.recipientDeviceToken },
        notification.title,
        notification.body,
        notification.deepLink ? { deepLink: notification.deepLink } : undefined,
      );
    } catch (error) {
      this.logger.error(`Push dispatch failed for notification ${notification.id}`, error);
    }
  }
}
