import {
  WebSocketGateway,
  WebSocketServer,
  SubscribeMessage,
  OnGatewayConnection,
  OnGatewayDisconnect,
} from '@nestjs/websockets';
import { Server, Socket } from 'socket.io';
import { Inject, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import * as jwt from 'jsonwebtoken';
import * as jwksRsa from 'jwks-rsa';
import {
  ConnectionRegistryPort,
  CONNECTION_REGISTRY_PORT,
} from '../../domain/port/outbound/connection-registry.port';
import {
  NotificationRepository,
  NOTIFICATION_REPOSITORY,
} from '../../domain/port/outbound/notification.repository';
import { DeliveryStatusValue } from '../../domain/model/delivery-status';

@WebSocketGateway({
  namespace: '/ws/notifications',
  cors: {
    origin: (process.env.CORS_ORIGINS ?? 'http://localhost:3000,http://localhost:5173').split(','),
    credentials: true,
  },
  pingInterval: 25000,
  pingTimeout: 10000,
})
export class SocketioNotificationGateway
  implements OnGatewayConnection, OnGatewayDisconnect
{
  @WebSocketServer()
  server!: Server;

  private readonly logger = new Logger(SocketioNotificationGateway.name);
  private readonly jwksClient: jwksRsa.JwksClient;

  constructor(
    private readonly configService: ConfigService,
    @Inject(CONNECTION_REGISTRY_PORT)
    private readonly connectionRegistry: ConnectionRegistryPort,
    @Inject(NOTIFICATION_REPOSITORY)
    private readonly notificationRepo: NotificationRepository,
  ) {
    const jwkSetUri = this.configService.get<string>(
      'KEYCLOAK_JWK_SET_URI',
      'http://localhost:8085/realms/vnshop/protocol/openid-connect/certs',
    );
    // jwks-rsa ships as CJS; handle both default-export and direct-call shapes
    const factory =
      (jwksRsa as unknown as { default?: typeof jwksRsa }).default ?? jwksRsa;
    this.jwksClient = (factory as typeof jwksRsa)({
      jwksUri: jwkSetUri,
      cache: true,
      rateLimit: true,
    });
  }

  async handleConnection(client: Socket): Promise<void> {
    try {
      const token = (client.handshake.auth?.token ?? client.handshake.query['token']) as string | undefined;
      if (!token) {
        client.disconnect(true);
        return;
      }

      const userId = await this.verifyToken(token);
      if (!userId) {
        client.disconnect(true);
        return;
      }

      (client as Socket & { userId: string }).userId = userId;
      await this.connectionRegistry.register(userId, client.id);
      void client.join(`user:${userId}`);

      // Refresh registration every 30s to keep TTL alive
      const refreshInterval = setInterval(async () => {
        await this.connectionRegistry.refreshRegistration(userId);
      }, 30_000);
      client.data['refreshInterval'] = refreshInterval;

      // Drain offline queue and send catch-up notifications
      const offlineIds = await this.connectionRegistry.drainOfflineQueue(userId);
      if (offlineIds.length > 0) {
        const notifications = await this.notificationRepo.findByIds(offlineIds);

        if (notifications.length > 0) {
          client.emit('notification:catch-up', notifications);
        }
      }

      this.logger.log(`Connected: ${client.id} userId=${userId}`);
    } catch (error) {
      this.logger.warn(`Connection rejected: ${client.id} - ${String(error)}`);
      client.disconnect(true);
    }
  }

  async handleDisconnect(client: Socket): Promise<void> {
    const userId = (client as Socket & { userId?: string }).userId;
    const interval = client.data?.['refreshInterval'] as NodeJS.Timeout | undefined;
    if (interval) clearInterval(interval);

    if (userId) {
      try {
        await this.connectionRegistry.unregister(userId, client.id);
        this.logger.log(`Disconnected: ${client.id} userId=${userId}`);
      } catch (err) {
        this.logger.warn(`Failed to unregister ${client.id}: ${err}`);
      }
    }
  }

  @SubscribeMessage('notification:ack')
  async handleAck(client: Socket, payload: { ids: string[] }): Promise<void> {
    const userId = (client as Socket & { userId?: string }).userId;
    if (!userId || !Array.isArray(payload?.ids) || payload.ids.length === 0) return;

    // Cap batch size to prevent abuse
    const ids = payload.ids.slice(0, 100);

    for (const id of ids) {
      try {
        const notification = await this.notificationRepo.findByIdAndUserId(id, userId);
        if (!notification) continue;
        if (notification.deliveryStatus.getValue() !== DeliveryStatusValue.SENT) continue;

        notification.markDelivered();
        await this.notificationRepo.save(notification);
      } catch (err) {
        this.logger.warn(`Failed to ACK notification ${id}: ${err}`);
      }
    }
  }

  private verifyToken(token: string): Promise<string | null> {
    return new Promise((resolve) => {
      jwt.verify(
        token,
        (header, callback) => {
          this.jwksClient.getSigningKey(header.kid, (err, key) => {
            if (err || !key) {
              callback(err ?? new Error('No signing key'));
              return;
            }
            callback(null, key.getPublicKey());
          });
        },
        { algorithms: ['RS256'] },
        (err, decoded) => {
          if (err || !decoded) {
            resolve(null);
            return;
          }
          resolve((decoded as jwt.JwtPayload).sub ?? null);
        },
      );
    });
  }
}
