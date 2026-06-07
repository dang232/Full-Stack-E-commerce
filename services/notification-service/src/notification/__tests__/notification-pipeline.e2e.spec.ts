import { Test } from '@nestjs/testing';
import { INestApplication } from '@nestjs/common';
import { MongoMemoryServer } from 'mongodb-memory-server';
import { io, Socket as ClientSocket } from 'socket.io-client';
import { IoAdapter } from '@nestjs/platform-socket.io';
import { ConfigModule } from '@nestjs/config';
import { MongooseModule } from '@nestjs/mongoose';
import RedisMock from 'ioredis-mock';
import { NotificationModule } from '../notification.module';
import { REDIS_CLIENT } from '../infrastructure/cache/redis.module';
import { SendNotificationUseCase } from '../application/command/send-notification.use-case';
import { NotificationType } from '../domain/model/notification-type.enum';
import { CountUnreadUseCase } from '../application/query/count-unread.use-case';

// Mock JWT verification to bypass Keycloak in tests
jest.mock('jsonwebtoken', () => ({
  verify: (
    _token: string,
    _getKey: unknown,
    _opts: unknown,
    callback: (err: Error | null, decoded?: unknown) => void,
  ) => {
    if (_token === 'e2e-valid-token') {
      callback(null, { sub: 'e2e-user-id' });
    } else {
      callback(new Error('Invalid token'));
    }
  },
}));

jest.mock('jwks-rsa', () => {
  const mockFactory = jest.fn(() => ({ getSigningKey: jest.fn() }));
  return {
    __esModule: true,
    default: mockFactory,
    // Named export used by JwtStrategy via passport-jwt
    passportJwtSecret: jest.fn(() => jest.fn()),
  };
});

describe('Notification Pipeline E2E', () => {
  let app: INestApplication;
  let mongod: MongoMemoryServer;
  let client: ClientSocket;
  let sendNotification: SendNotificationUseCase;
  let countUnread: CountUnreadUseCase;

  beforeAll(async () => {
    mongod = await MongoMemoryServer.create();

    const module = await Test.createTestingModule({
      imports: [
        ConfigModule.forRoot({
          isGlobal: true,
          load: [
            () => ({
              MONGO_URI: mongod.getUri(),
              REDIS_URL: 'redis://localhost:6379',
              KAFKA_BOOTSTRAP_SERVERS: 'localhost:9092',
              KEYCLOAK_JWK_SET_URI:
                'http://localhost:8085/realms/vnshop/protocol/openid-connect/certs',
            }),
          ],
        }),
        MongooseModule.forRoot(mongod.getUri()),
        NotificationModule,
      ],
    })
      .overrideProvider(REDIS_CLIENT)
      .useValue(new RedisMock())
      .compile();

    app = module.createNestApplication();
    app.useWebSocketAdapter(new IoAdapter(app));
    await app.init();
    await app.listen(0);

    sendNotification = module.get(SendNotificationUseCase);
    countUnread = module.get(CountUnreadUseCase);

    // eslint-disable-next-line @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access
    const port = (app.getHttpServer().address() as { port: number }).port;
    client = io(`http://localhost:${port}/ws/notifications`, {
      query: { token: 'e2e-valid-token' },
      transports: ['websocket'],
    });
    await new Promise<void>((resolve, reject) => {
      client.on('connect', resolve);
      client.on('connect_error', reject);
      setTimeout(() => reject(new Error('Connection timeout')), 5000);
    });
  }, 30000);

  afterAll(async () => {
    client?.offAny();
    client?.disconnect();
    await app?.close();
    await mongod?.stop();
  });

  it('full pipeline: send notification → persist → deliver via WebSocket', async () => {
    const received = new Promise<any>((resolve) => {
      client.on('notification:new', (data: any) => resolve(data));
    });

    await sendNotification.execute({
      userId: 'e2e-user-id',
      type: NotificationType.ORDER_CREATED,
      title: 'E2E: Đặt hàng thành công',
      body: 'Đơn hàng #E2E-001 đã được đặt.',
      deepLink: '/orders/E2E-001',
      threadId: 'order:E2E-001',
      threadTitle: 'Đơn hàng #E2E-001',
      metadata: { orderId: 'E2E-001' },
      idempotencyKey: 'e2e-test-1',
    });

    // eslint-disable-next-line @typescript-eslint/no-unsafe-assignment
    const payload = await Promise.race([
      received,
      new Promise((_, reject) =>
        setTimeout(() => reject(new Error('WebSocket timeout')), 5000),
      ),
    ]);

    // eslint-disable-next-line @typescript-eslint/no-unsafe-member-access
    expect(payload.title).toBe('E2E: Đặt hàng thành công');
    // eslint-disable-next-line @typescript-eslint/no-unsafe-member-access
    expect(payload.body).toContain('E2E-001');
    // eslint-disable-next-line @typescript-eslint/no-unsafe-member-access
    expect(payload.deepLink).toBe('/orders/E2E-001');
    // eslint-disable-next-line @typescript-eslint/no-unsafe-member-access
    expect(payload.type).toBe('ORDER_CREATED');
  });

  it('persists notification and increments unread count', async () => {
    const count = await countUnread.execute('e2e-user-id');
    expect(count).toBeGreaterThanOrEqual(1);
  });

  it('deduplication prevents duplicate notifications', async () => {
    const countBefore = await countUnread.execute('e2e-user-id');

    // Send same idempotency key again
    await sendNotification.execute({
      userId: 'e2e-user-id',
      type: NotificationType.ORDER_CREATED,
      title: 'Duplicate',
      body: 'Should not create new',
      idempotencyKey: 'e2e-test-1',
    });

    const countAfter = await countUnread.execute('e2e-user-id');
    expect(countAfter).toBe(countBefore); // No new notification
  });
});
