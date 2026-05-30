import { Test } from '@nestjs/testing';
import { INestApplication } from '@nestjs/common';
import { IoAdapter } from '@nestjs/platform-socket.io';
import { ConfigService } from '@nestjs/config';
import { io, Socket as ClientSocket } from 'socket.io-client';
import { SocketioNotificationGateway } from '../socketio-notification.gateway';
import { CONNECTION_REGISTRY_PORT } from '../../../domain/port/outbound/connection-registry.port';
import { NOTIFICATION_REPOSITORY } from '../../../domain/port/outbound/notification.repository';

// Mock jsonwebtoken to bypass JWKS verification in tests
jest.mock('jsonwebtoken', () => ({
  verify: (
    _token: string,
    _getKey: unknown,
    _opts: unknown,
    callback: (err: Error | null, decoded?: unknown) => void,
  ) => {
    if (_token === 'valid-token') {
      callback(null, { sub: 'test-user-id' });
    } else {
      callback(new Error('Invalid token'));
    }
  },
}));

jest.mock('jwks-rsa', () => () => ({
  getSigningKey: jest.fn(),
}));

describe('SocketioNotificationGateway', () => {
  let app: INestApplication;
  let port: number;

  const mockRegistry = {
    register: jest.fn().mockResolvedValue(undefined),
    unregister: jest.fn().mockResolvedValue(undefined),
    getSocketIds: jest.fn().mockResolvedValue([]),
    isOnline: jest.fn().mockResolvedValue(false),
    enqueueOffline: jest.fn().mockResolvedValue(undefined),
    drainOfflineQueue: jest.fn().mockResolvedValue([]),
  };

  const mockRepo = {
    findById: jest.fn().mockResolvedValue(null),
    findByIds: jest.fn().mockResolvedValue([]),
    save: jest.fn(),
    findByIdAndUserId: jest.fn(),
    findByIdempotencyKey: jest.fn(),
    findByUser: jest.fn(),
    findThreadsByUser: jest.fn(),
    findByThread: jest.fn(),
    countUnread: jest.fn(),
    markAllReadForUser: jest.fn(),
  };

  const mockConfig = {
    get: jest
      .fn()
      .mockReturnValue(
        'http://localhost:8085/realms/vnshop/protocol/openid-connect/certs',
      ),
  };

  beforeAll(async () => {
    const module = await Test.createTestingModule({
      providers: [
        SocketioNotificationGateway,
        { provide: CONNECTION_REGISTRY_PORT, useValue: mockRegistry },
        { provide: NOTIFICATION_REPOSITORY, useValue: mockRepo },
        { provide: ConfigService, useValue: mockConfig },
      ],
    }).compile();

    app = module.createNestApplication();
    app.useWebSocketAdapter(new IoAdapter(app));
    await app.init();
    await app.listen(0);
    const address = app.getHttpServer().address() as { port: number };
    port = address.port;
  });

  afterAll(async () => {
    await app.close();
  });

  beforeEach(() => {
    jest.clearAllMocks();
    mockRegistry.drainOfflineQueue.mockResolvedValue([]);
  });

  it('connects with valid token and registers in connection registry', async () => {
    const client: ClientSocket = io(
      `http://localhost:${port}/ws/notifications`,
      {
        query: { token: 'valid-token' },
        transports: ['websocket'],
      },
    );

    await new Promise<void>((resolve, reject) => {
      client.on('connect', resolve);
      client.on('connect_error', reject);
      setTimeout(() => reject(new Error('connect timeout')), 3000);
    });

    expect(client.connected).toBe(true);

    // Give the gateway time to process handleConnection
    await new Promise((r) => setTimeout(r, 150));
    expect(mockRegistry.register).toHaveBeenCalledWith(
      'test-user-id',
      expect.any(String),
    );

    client.disconnect();
  });

  it('disconnects client with invalid token', async () => {
    const client: ClientSocket = io(
      `http://localhost:${port}/ws/notifications`,
      {
        query: { token: 'invalid-token' },
        transports: ['websocket'],
        reconnection: false,
      },
    );

    await new Promise<void>((resolve) => {
      client.on('disconnect', () => resolve());
      setTimeout(resolve, 2000);
    });

    expect(client.connected).toBe(false);
    client.disconnect();
  });

  it('disconnects client with no token', async () => {
    const client: ClientSocket = io(
      `http://localhost:${port}/ws/notifications`,
      {
        transports: ['websocket'],
        reconnection: false,
      },
    );

    await new Promise<void>((resolve) => {
      client.on('disconnect', () => resolve());
      setTimeout(resolve, 2000);
    });

    expect(client.connected).toBe(false);
    client.disconnect();
  });

  it('sends catch-up notifications when offline queue is non-empty', async () => {
    const fakeNotification = {
      id: 'notif-1',
      type: 'ORDER_CREATED',
      title: 'Test',
      body: 'Body',
    };
    mockRegistry.drainOfflineQueue.mockResolvedValue(['notif-1']);
    mockRepo.findByIds.mockResolvedValue([fakeNotification]);

    const client: ClientSocket = io(
      `http://localhost:${port}/ws/notifications`,
      {
        query: { token: 'valid-token' },
        transports: ['websocket'],
      },
    );

    const catchUpPromise = new Promise<unknown[]>((resolve) => {
      client.on('notification:catch-up', (data: unknown[]) => resolve(data));
    });

    await new Promise<void>((resolve, reject) => {
      client.on('connect', resolve);
      client.on('connect_error', reject);
      setTimeout(() => reject(new Error('connect timeout')), 3000);
    });

    const catchUp = await Promise.race([
      catchUpPromise,
      new Promise<never>((_, reject) =>
        setTimeout(() => reject(new Error('catch-up timeout')), 2000),
      ),
    ]);

    expect(catchUp).toEqual(
      expect.arrayContaining([expect.objectContaining({ id: 'notif-1' })]),
    );

    client.disconnect();
  });
});
