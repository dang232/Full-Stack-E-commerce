import { MikroOrmModule } from '@mikro-orm/nestjs';
import { Module } from '@nestjs/common';
import { PassportModule } from '@nestjs/passport';
import { CountUnreadNotificationsUseCase } from './application/count-unread-notifications.use-case';
import { FindNotificationByIdUseCase } from './application/find-notification-by-id.use-case';
import { FindUserNotificationsUseCase } from './application/find-user-notifications.use-case';
import { KafkaNotificationConsumer } from './application/kafka-notification.consumer';
import { MarkAllNotificationsReadUseCase } from './application/mark-all-notifications-read.use-case';
import { MarkNotificationReadUseCase } from './application/mark-notification-read.use-case';
import {
  NotificationChannel,
  SendNotificationUseCase,
} from './application/send-notification.use-case';
import {
  NOTIFICATION_REPOSITORY,
  NotificationRepository,
} from './domain/notification.repository';
import { JwtStrategy } from './infrastructure/auth/jwt.strategy';
import { ConsoleChannelAdapter } from './infrastructure/channel/console-channel.adapter';
import { EmailChannelAdapter } from './infrastructure/channel/email-channel.adapter';
import { NotificationController } from './infrastructure/notification.controller';
import { NotificationMikroOrmEntity } from './infrastructure/notification.mikro-orm-entity';
import { NotificationMikroOrmRepository } from './infrastructure/notification.mikro-orm-repository';

export const NOTIFICATION_CHANNELS = Symbol('NOTIFICATION_CHANNELS');

@Module({
  imports: [
    MikroOrmModule.forFeature([NotificationMikroOrmEntity]),
    PassportModule.register({ defaultStrategy: 'jwt' }),
  ],
  controllers: [NotificationController, KafkaNotificationConsumer],
  providers: [
    JwtStrategy,
    ConsoleChannelAdapter,
    EmailChannelAdapter,
    {
      provide: NOTIFICATION_CHANNELS,
      useFactory: (
        consoleChannel: ConsoleChannelAdapter,
        emailChannel: EmailChannelAdapter,
      ): NotificationChannel[] => [consoleChannel, emailChannel],
      inject: [ConsoleChannelAdapter, EmailChannelAdapter],
    },
    {
      provide: NOTIFICATION_REPOSITORY,
      useClass: NotificationMikroOrmRepository,
    },
    {
      provide: FindUserNotificationsUseCase,
      useFactory: (
        repository: NotificationRepository,
      ): FindUserNotificationsUseCase =>
        new FindUserNotificationsUseCase(repository),
      inject: [NOTIFICATION_REPOSITORY],
    },
    {
      provide: FindNotificationByIdUseCase,
      useFactory: (
        repository: NotificationRepository,
      ): FindNotificationByIdUseCase =>
        new FindNotificationByIdUseCase(repository),
      inject: [NOTIFICATION_REPOSITORY],
    },
    {
      provide: MarkNotificationReadUseCase,
      useFactory: (
        repository: NotificationRepository,
      ): MarkNotificationReadUseCase =>
        new MarkNotificationReadUseCase(repository),
      inject: [NOTIFICATION_REPOSITORY],
    },
    {
      provide: MarkAllNotificationsReadUseCase,
      useFactory: (
        repository: NotificationRepository,
      ): MarkAllNotificationsReadUseCase =>
        new MarkAllNotificationsReadUseCase(repository),
      inject: [NOTIFICATION_REPOSITORY],
    },
    {
      provide: CountUnreadNotificationsUseCase,
      useFactory: (
        repository: NotificationRepository,
      ): CountUnreadNotificationsUseCase =>
        new CountUnreadNotificationsUseCase(repository),
      inject: [NOTIFICATION_REPOSITORY],
    },
    {
      provide: SendNotificationUseCase,
      useFactory: (
        repository: NotificationRepository,
        channels: NotificationChannel[],
      ): SendNotificationUseCase =>
        new SendNotificationUseCase(repository, channels),
      inject: [NOTIFICATION_REPOSITORY, NOTIFICATION_CHANNELS],
    },
  ],
})
export class NotificationModule {}
