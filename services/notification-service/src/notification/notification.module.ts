import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { FindNotificationByIdUseCase } from './application/find-notification-by-id.use-case';
import { FindUserNotificationsUseCase } from './application/find-user-notifications.use-case';
import { KafkaNotificationConsumer } from './application/kafka-notification.consumer';
import { NotificationChannel, SendNotificationUseCase } from './application/send-notification.use-case';
import { NOTIFICATION_REPOSITORY, NotificationRepository } from './domain/notification.repository';
import { ConsoleChannelAdapter } from './infrastructure/channel/console-channel.adapter';
import { EmailChannelAdapter } from './infrastructure/channel/email-channel.adapter';
import { NotificationController } from './infrastructure/notification.controller';
import { NotificationTypeOrmEntity } from './infrastructure/notification.typeorm-entity';
import { NotificationTypeOrmRepository } from './infrastructure/notification.typeorm-repository';

export const NOTIFICATION_CHANNELS = Symbol('NOTIFICATION_CHANNELS');

@Module({
  imports: [TypeOrmModule.forFeature([NotificationTypeOrmEntity])],
  controllers: [NotificationController, KafkaNotificationConsumer],
  providers: [
    ConsoleChannelAdapter,
    EmailChannelAdapter,
    {
      provide: NOTIFICATION_CHANNELS,
      useFactory: (consoleChannel: ConsoleChannelAdapter, emailChannel: EmailChannelAdapter): NotificationChannel[] => [
        consoleChannel,
        emailChannel,
      ],
      inject: [ConsoleChannelAdapter, EmailChannelAdapter],
    },
    {
      provide: NOTIFICATION_REPOSITORY,
      useClass: NotificationTypeOrmRepository,
    },
    {
      provide: FindUserNotificationsUseCase,
      useFactory: (repository: NotificationRepository): FindUserNotificationsUseCase =>
        new FindUserNotificationsUseCase(repository),
      inject: [NOTIFICATION_REPOSITORY],
    },
    {
      provide: FindNotificationByIdUseCase,
      useFactory: (repository: NotificationRepository): FindNotificationByIdUseCase =>
        new FindNotificationByIdUseCase(repository),
      inject: [NOTIFICATION_REPOSITORY],
    },
    {
      provide: SendNotificationUseCase,
      useFactory: (repository: NotificationRepository, channels: NotificationChannel[]): SendNotificationUseCase =>
        new SendNotificationUseCase(repository, channels),
      inject: [NOTIFICATION_REPOSITORY, NOTIFICATION_CHANNELS],
    },
  ],
})
export class NotificationModule {}
