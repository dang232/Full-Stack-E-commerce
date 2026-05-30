import { Injectable } from '@nestjs/common';
import { Server } from 'socket.io';
import {
  RealtimeChannelPort,
} from '../../domain/port/outbound/realtime-channel.port';
import { Notification } from '../../domain/model/notification';
import { SocketioNotificationGateway } from './socketio-notification.gateway';

@Injectable()
export class SocketioRealtimeChannelAdapter implements RealtimeChannelPort {
  constructor(private readonly gateway: SocketioNotificationGateway) {}

  private get server(): Server {
    return this.gateway.server;
  }

  async sendToUser(userId: string, notification: Notification): Promise<void> {
    this.server
      .to(`user:${userId}`)
      .emit('notification:new', this.toPayload(notification));
  }

  async sendBatchToUser(
    userId: string,
    notifications: Notification[],
  ): Promise<void> {
    this.server
      .to(`user:${userId}`)
      .emit(
        'notification:catch-up',
        notifications.map((n) => this.toPayload(n)),
      );
  }

  private toPayload(notification: Notification): Record<string, unknown> {
    return {
      id: notification.id,
      type: notification.type,
      title: notification.title,
      body: notification.body,
      deepLink: notification.deepLink,
      priority: notification.priority,
      threadId: notification.thread?.threadId ?? null,
      threadTitle: notification.thread?.threadTitle ?? null,
      read: notification.read,
      createdAt: notification.createdAt.toISOString(),
    };
  }
}
