import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
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
      provide: SendNotificationUseCase,
      useFactory: (repository: NotificationRepository, channels: NotificationChannel[]): SendNotificationUseCase =>
        new SendNotificationUseCase(repository, channels),
      inject: [NOTIFICATION_REPOSITORY, NOTIFICATION_CHANNELS],
    },
  ],
})
export class NotificationModule {}
