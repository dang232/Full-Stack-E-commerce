import { Inject, Injectable, Logger } from '@nestjs/common';
import {
  NOTIFICATION_REPOSITORY,
  NotificationRepository,
} from '../../domain/port/outbound/notification.repository';
import {
  REALTIME_CHANNEL_PORT,
  RealtimeChannelPort,
} from '../../domain/port/outbound/realtime-channel.port';
import {
  CONNECTION_REGISTRY_PORT,
  ConnectionRegistryPort,
} from '../../domain/port/outbound/connection-registry.port';

@Injectable()
export class RetryFailedDeliveriesUseCase {
  private readonly logger = new Logger(RetryFailedDeliveriesUseCase.name);

  constructor(
    @Inject(NOTIFICATION_REPOSITORY)
    private readonly repo: NotificationRepository,
    @Inject(REALTIME_CHANNEL_PORT)
    private readonly channel: RealtimeChannelPort,
    @Inject(CONNECTION_REGISTRY_PORT)
    private readonly registry: ConnectionRegistryPort,
  ) {}

  execute(): { retried: number; movedToDlq: number } {
    // Retry logic is handled inline in the event handler.
    // This use case is a placeholder for a scheduled job that would
    // query FAILED notifications and re-enqueue them.
    this.logger.debug('RetryFailedDeliveriesUseCase: no-op placeholder');
    return { retried: 0, movedToDlq: 0 };
  }
}
