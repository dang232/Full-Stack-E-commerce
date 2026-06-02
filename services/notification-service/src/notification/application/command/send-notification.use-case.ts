import { Inject, Injectable, Logger } from '@nestjs/common';
import { EventEmitter2 } from '@nestjs/event-emitter';
import { Notification } from '../../domain/model/notification';
import { NotificationType } from '../../domain/model/notification-type.enum';
import { Priority } from '../../domain/model/priority.enum';
import { NotificationThread } from '../../domain/model/notification-thread';
import { NotificationChannel } from '../../domain/model/notification-preferences';
import { NotificationCreatedEvent } from '../../domain/event/notification-created.event';
import {
  NOTIFICATION_REPOSITORY,
  NotificationRepository,
} from '../../domain/port/outbound/notification.repository';
import {
  NOTIFICATION_PREFERENCES_REPOSITORY,
  NotificationPreferencesRepository,
} from '../../domain/port/outbound/notification-preferences.repository';
import {
  DEDUPLICATION_PORT,
  DeduplicationPort,
} from '../../domain/port/outbound/deduplication.port';

export interface SendNotificationCommand {
  userId: string;
  type: NotificationType;
  title: string;
  body: string;
  deepLink?: string;
  priority?: Priority;
  threadId?: string;
  threadTitle?: string;
  metadata?: Record<string, unknown>;
  idempotencyKey?: string;
}

@Injectable()
export class SendNotificationUseCase {
  private readonly logger = new Logger(SendNotificationUseCase.name);

  constructor(
    @Inject(NOTIFICATION_REPOSITORY) private readonly repo: NotificationRepository,
    @Inject(NOTIFICATION_PREFERENCES_REPOSITORY)
    private readonly prefsRepo: NotificationPreferencesRepository,
    @Inject(DEDUPLICATION_PORT) private readonly dedup: DeduplicationPort,
    private readonly eventEmitter: EventEmitter2,
  ) {}

  async execute(command: SendNotificationCommand): Promise<Notification | null> {
    // Check user preferences before persisting
    const prefs = await this.prefsRepo.findByUserId(command.userId);
    const allChannels = Object.values(NotificationChannel);
    const suppressedChannels: NotificationChannel[] = [];

    if (prefs) {
      for (const channel of allChannels) {
        if (!prefs.isChannelEnabled(command.type, channel)) {
          suppressedChannels.push(channel);
        }
      }

      // If ALL channels are suppressed, short-circuit — no persistence or events
      if (suppressedChannels.length === allChannels.length) {
        this.logger.debug(
          `Notification suppressed: all channels disabled for type=${command.type} user=${command.userId}`,
        );
        return null;
      }
    }

    // Deduplication check
    if (command.idempotencyKey) {
      const acquired = await this.dedup.tryAcquire(command.idempotencyKey);
      if (!acquired) {
        // Another process already claimed this key
        const existing = await this.repo.findByIdempotencyKey(command.idempotencyKey);
        if (existing) {
          this.logger.debug(`Duplicate notification skipped: ${command.idempotencyKey}`);
          return existing;
        }
        // Key exists but notification not found (rare: key set but save failed) — proceed
      }
    }

    // Build thread value object if provided
    const thread =
      command.threadId && command.threadTitle
        ? NotificationThread.create(command.threadId, command.threadTitle)
        : undefined;

    // Create aggregate
    const notification = Notification.create({
      userId: command.userId,
      type: command.type,
      title: command.title,
      body: command.body,
      deepLink: command.deepLink,
      priority: command.priority,
      thread,
      metadata: command.metadata,
      idempotencyKey: command.idempotencyKey,
    });

    // Persist
    await this.repo.save(notification);

    // Emit domain events with suppressed channels info for downstream handlers
    const events = notification.pullDomainEvents();
    for (const event of events) {
      if (event instanceof NotificationCreatedEvent && suppressedChannels.length > 0) {
        // Re-emit with suppressedChannels so downstream handlers know which channels to skip
        this.eventEmitter.emit(
          'notification.created',
          new NotificationCreatedEvent(
            event.notificationId,
            event.userId,
            event.type,
            suppressedChannels,
          ),
        );
      } else {
        this.eventEmitter.emit('notification.created', event);
      }
    }

    this.logger.log(
      `Notification created: id=${notification.id} type=${notification.type} user=${notification.userId}`,
    );
    return notification;
  }
}
