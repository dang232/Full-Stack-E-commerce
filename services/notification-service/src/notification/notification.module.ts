import { Module } from '@nestjs/common';
import { MongooseModule } from '@nestjs/mongoose';
import { PassportModule } from '@nestjs/passport';
import { EventEmitterModule } from '@nestjs/event-emitter';

// Infrastructure — Persistence
import {
  NotificationSchemaClass,
  NotificationSchema,
} from './infrastructure/persistence/mongo-notification.schema';
import { MongoNotificationRepository } from './infrastructure/persistence/mongo-notification.repository';

// Infrastructure — Cache
import { RedisModule } from './infrastructure/cache/redis.module';
import { RedisDeduplicationAdapter } from './infrastructure/cache/redis-deduplication.adapter';
import { RedisConnectionRegistryAdapter } from './infrastructure/cache/redis-connection-registry.adapter';

// Infrastructure — Realtime
import { SocketioNotificationGateway } from './infrastructure/realtime/socketio-notification.gateway';
import { SocketioRealtimeChannelAdapter } from './infrastructure/realtime/socketio-realtime-channel.adapter';

// Infrastructure — Messaging
import { KafkaEventConsumer } from './infrastructure/messaging/kafka-event.consumer';

// Infrastructure — REST
import { NotificationRestController } from './infrastructure/rest/notification.controller';

// Infrastructure — Auth
import { JwtStrategy } from './infrastructure/auth/jwt.strategy';

// Application — Commands
import { SendNotificationUseCase } from './application/command/send-notification.use-case';
import { MarkNotificationReadUseCase } from './application/command/mark-notification-read.use-case';
import { MarkAllReadUseCase } from './application/command/mark-all-read.use-case';
import { RetryFailedDeliveriesUseCase } from './application/command/retry-failed-deliveries.use-case';

// Application — Queries
import { FindUserNotificationsUseCase } from './application/query/find-user-notifications.use-case';
import { FindNotificationThreadsUseCase } from './application/query/find-notification-threads.use-case';
import { FindThreadNotificationsUseCase } from './application/query/find-thread-notifications.use-case';
import { CountUnreadUseCase } from './application/query/count-unread.use-case';

// Application — Event Handlers
import { NotificationCreatedHandler } from './application/event-handler/notification-created.handler';

// Domain — Port Symbols
import { NOTIFICATION_REPOSITORY } from './domain/port/outbound/notification.repository';
import { REALTIME_CHANNEL_PORT } from './domain/port/outbound/realtime-channel.port';
import { DEDUPLICATION_PORT } from './domain/port/outbound/deduplication.port';
import { CONNECTION_REGISTRY_PORT } from './domain/port/outbound/connection-registry.port';

@Module({
  imports: [
    MongooseModule.forFeature([
      { name: NotificationSchemaClass.name, schema: NotificationSchema },
    ]),
    PassportModule.register({ defaultStrategy: 'jwt' }),
    EventEmitterModule.forRoot(),
    RedisModule,
  ],
  controllers: [NotificationRestController, KafkaEventConsumer],
  providers: [
    // Auth
    JwtStrategy,

    // Port → Adapter bindings
    { provide: NOTIFICATION_REPOSITORY, useClass: MongoNotificationRepository },
    { provide: REALTIME_CHANNEL_PORT, useClass: SocketioRealtimeChannelAdapter },
    { provide: DEDUPLICATION_PORT, useClass: RedisDeduplicationAdapter },
    { provide: CONNECTION_REGISTRY_PORT, useClass: RedisConnectionRegistryAdapter },

    // Infrastructure (needed for DI resolution)
    SocketioNotificationGateway,
    SocketioRealtimeChannelAdapter,

    // Application — Commands
    SendNotificationUseCase,
    MarkNotificationReadUseCase,
    MarkAllReadUseCase,
    RetryFailedDeliveriesUseCase,

    // Application — Queries
    FindUserNotificationsUseCase,
    FindNotificationThreadsUseCase,
    FindThreadNotificationsUseCase,
    CountUnreadUseCase,

    // Application — Event Handlers
    NotificationCreatedHandler,
  ],
})
export class NotificationModule {}
