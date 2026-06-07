import { Test } from '@nestjs/testing';
import { INestApplication } from '@nestjs/common';
import { IoAdapter } from '@nestjs/platform-socket.io';
import { ConfigService } from '@nestjs/config';
import { io, Socket as ClientSocket } from 'socket.io-client';
import { SocketioNotificationGateway } from '../socketio-notification.gateway';
import { CONNECTION_REGISTRY_PORT } from '../../../domain/port/outbound/connection-registry.port';
import { NOTIFICATION_REPOSITORY } from '../../../domain/port/outbound/notification.repository';
import { NOTIFICATION_PREFERENCES_REPOSITORY } from '../../../domain/port/outbound/notification-preferences.repository';
import { Notification } from '../../../domain/model/notification';
import { NotificationType } from '../../../domain/model/notification-type.enum';
import { NotificationPreferences } from '../../../domain/model/notification-preferences';
import { DeliveryStatusValue } from '../../../domain/model/delivery-status';

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

  const mockPrefsRepo = {
    findByUserId: jest.fn().mockResolvedValue(null),
    save: jest.fn().mockResolvedValue(undefined),
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
        {
          provide: NOTIFICATION_PREFERENCES_REPOSITORY,
          useValue: mockPrefsRepo,
        },
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

  function waitForSocketEvent(
    client: ClientSocket,
    event: 'connect' | 'disconnect',
    timeoutMessage: string,
    timeoutMs = 3000,
  ): Promise<void> {
    return new Promise((resolve, reject) => {
      let timeout: NodeJS.Timeout;

      function cleanup() {
        clearTimeout(timeout);
        client.off(event, onEvent);
        client.off('connect_error', onError);
      }

      function onEvent() {
        cleanup();
        resolve();
      }

      function onError(error: Error) {
        cleanup();
        reject(error);
      }

      timeout = setTimeout(() => {
        cleanup();
        reject(new Error(timeoutMessage));
      }, timeoutMs);

      client.once(event, onEvent);
      if (event !== 'disconnect') {
        client.once('connect_error', onError);
      }
    });
  }

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

    await waitForSocketEvent(client, 'connect', 'connect timeout');

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

    await waitForSocketEvent(client, 'disconnect', 'disconnect timeout', 2000);

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

    await waitForSocketEvent(client, 'disconnect', 'disconnect timeout', 2000);

    expect(client.connected).toBe(false);
    client.disconnect();
  });

  it('skips catch-up emit when findByIds returns empty array', async () => {
    mockRegistry.drainOfflineQueue.mockResolvedValue(['notif-missing']);
    mockRepo.findByIds.mockResolvedValue([]); // notifications.length === 0 → skip

    const client: ClientSocket = io(
      `http://localhost:${port}/ws/notifications`,
      {
        query: { token: 'valid-token' },
        transports: ['websocket'],
      },
    );

    await waitForSocketEvent(client, 'connect', 'connect timeout');

    await new Promise((r) => setTimeout(r, 200));
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

    await waitForSocketEvent(client, 'connect', 'connect timeout');

    const catchUpTimeout = new Promise<never>((_, reject) => {
      const timeout = setTimeout(() => {
        reject(new Error('catch-up timeout'));
      }, 2000);
      catchUpPromise.finally(() => clearTimeout(timeout));
    });

    const catchUp = await Promise.race([catchUpPromise, catchUpTimeout]);

    expect(catchUp).toEqual(
      expect.arrayContaining([expect.objectContaining({ id: 'notif-1' })]),
    );

    client.disconnect();
  });
});

// Unit-style tests for handleDisconnect and handleAck branches
// (no real socket server — uses the gateway instance directly)
describe('SocketioNotificationGateway unit', () => {
  let gateway: SocketioNotificationGateway;

  const mockRegistryUnit = {
    register: jest.fn().mockResolvedValue(undefined),
    unregister: jest.fn().mockResolvedValue(undefined),
    isOnline: jest.fn().mockResolvedValue(false),
    refreshRegistration: jest.fn().mockResolvedValue(undefined),
    drainOfflineQueue: jest.fn().mockResolvedValue([]),
    enqueueOffline: jest.fn().mockResolvedValue(undefined),
  };
  const mockRepoUnit = {
    findByIdAndUserId: jest.fn(),
    findByIds: jest.fn().mockResolvedValue([]),
    save: jest.fn().mockResolvedValue(undefined),
  };
  const mockPrefsRepoUnit = {
    findByUserId: jest.fn().mockResolvedValue(null),
    save: jest.fn().mockResolvedValue(undefined),
  };
  const mockConfigUnit = {
    get: jest.fn((key: string, fallback?: string) => fallback ?? ''),
  };

  beforeEach(async () => {
    jest.clearAllMocks();
    const module = await Test.createTestingModule({
      providers: [
        SocketioNotificationGateway,
        { provide: ConfigService, useValue: mockConfigUnit },
        { provide: CONNECTION_REGISTRY_PORT, useValue: mockRegistryUnit },
        { provide: NOTIFICATION_REPOSITORY, useValue: mockRepoUnit },
        {
          provide: NOTIFICATION_PREFERENCES_REPOSITORY,
          useValue: mockPrefsRepoUnit,
        },
      ],
    }).compile();
    gateway = module.get(SocketioNotificationGateway);
  });

  function makeSocket(overrides: Record<string, unknown> = {}) {
    return {
      id: 'sock-unit',
      handshake: { auth: {}, query: {} },
      data: {},
      disconnect: jest.fn(),
      join: jest.fn(),
      emit: jest.fn(),
      ...overrides,
    } as any;
  }

  describe('handleDisconnect', () => {
    it('calls unregister when userId is set', async () => {
      const client = makeSocket();
      client.userId = 'user-d1';
      await gateway.handleDisconnect(client);
      expect(mockRegistryUnit.unregister).toHaveBeenCalledWith(
        'user-d1',
        'sock-unit',
      );
    });

    it('does not call unregister when userId not set', async () => {
      const client = makeSocket();
      await gateway.handleDisconnect(client);
      expect(mockRegistryUnit.unregister).not.toHaveBeenCalled();
    });

    it('clears refreshInterval when present on socket data', async () => {
      const client = makeSocket();
      client.userId = 'user-d2';
      const interval = setInterval(() => {}, 60000);
      client.data = { refreshInterval: interval };
      await gateway.handleDisconnect(client);
      expect(mockRegistryUnit.unregister).toHaveBeenCalled();
    });

    it('does not throw when unregister rejects', async () => {
      const client = makeSocket();
      client.userId = 'user-d3';
      client.data = {};
      mockRegistryUnit.unregister.mockRejectedValueOnce(
        new Error('Redis error'),
      );
      await expect(gateway.handleDisconnect(client)).resolves.not.toThrow();
    });
  });

  describe('handleAck', () => {
    it('does nothing when userId not set', async () => {
      const client = makeSocket();
      await gateway.handleAck(client, { ids: ['n1'] });
      expect(mockRepoUnit.findByIdAndUserId).not.toHaveBeenCalled();
    });

    it('does nothing when ids is empty', async () => {
      const client = makeSocket();
      client.userId = 'user-a1';
      await gateway.handleAck(client, { ids: [] });
      expect(mockRepoUnit.findByIdAndUserId).not.toHaveBeenCalled();
    });

    it('does nothing when ids is not an array', async () => {
      const client = makeSocket();
      client.userId = 'user-a2';
      await gateway.handleAck(client, { ids: null as any });
      expect(mockRepoUnit.findByIdAndUserId).not.toHaveBeenCalled();
    });

    it('marks SENT notification as DELIVERED', async () => {
      const notification = Notification.create({
        userId: 'user-a3',
        type: NotificationType.ORDER_CREATED,
        title: 'T',
        body: 'B',
      });
      notification.markSent();
      const client = makeSocket();
      client.userId = 'user-a3';
      mockRepoUnit.findByIdAndUserId.mockResolvedValueOnce(notification);

      await gateway.handleAck(client, { ids: [notification.id] });

      expect(notification.deliveryStatus.getValue()).toBe(
        DeliveryStatusValue.DELIVERED,
      );
      expect(mockRepoUnit.save).toHaveBeenCalledWith(notification);
    });

    it('skips notification with non-SENT status', async () => {
      const notification = Notification.create({
        userId: 'user-a4',
        type: NotificationType.ORDER_CREATED,
        title: 'T',
        body: 'B',
      });
      // status is QUEUED
      const client = makeSocket();
      client.userId = 'user-a4';
      mockRepoUnit.findByIdAndUserId.mockResolvedValueOnce(notification);

      await gateway.handleAck(client, { ids: [notification.id] });

      expect(mockRepoUnit.save).not.toHaveBeenCalled();
    });

    it('skips when notification not found', async () => {
      const client = makeSocket();
      client.userId = 'user-a5';
      mockRepoUnit.findByIdAndUserId.mockResolvedValueOnce(null);

      await gateway.handleAck(client, { ids: ['missing'] });

      expect(mockRepoUnit.save).not.toHaveBeenCalled();
    });

    it('does not throw when repo rejects', async () => {
      const client = makeSocket();
      client.userId = 'user-a6';
      mockRepoUnit.findByIdAndUserId.mockRejectedValueOnce(
        new Error('DB down'),
      );

      await expect(
        gateway.handleAck(client, { ids: ['notif-err'] }),
      ).resolves.not.toThrow();
    });
  });

  describe('handleConnection catch-up filtering', () => {
    it('skips catch-up emit when prefs filter out all notifications (filtered.length===0)', async () => {
      // Simulate the filtered.length > 0 false branch by returning prefs with IN_APP disabled
      const notif = Notification.create({
        userId: 'test-user-id',
        type: NotificationType.ORDER_CREATED,
        title: 'T',
        body: 'B',
      });
      const prefs = NotificationPreferences.createDefault('test-user-id');
      prefs.setTypeChannels(NotificationType.ORDER_CREATED, []); // IN_APP disabled

      mockRegistryUnit.drainOfflineQueue.mockResolvedValue([notif.id]);
      mockRepoUnit.findByIds.mockResolvedValue([notif]);
      mockPrefsRepoUnit.findByUserId.mockResolvedValue(prefs);

      const client = makeSocket();
      client.handshake = { auth: { token: 'valid-token' }, query: {} } as any;

      // Mock jwt.verify to succeed
      const jwt = require('jsonwebtoken') as typeof import('jsonwebtoken');
      jest
        .spyOn(jwt, 'verify')
        .mockImplementationOnce((_t, _g, _o, cb: any) => {
          cb(null, { sub: 'test-user-id' });
        });

      await gateway.handleConnection(client);

      // filtered.length === 0 → no emit
      expect(client.emit).not.toHaveBeenCalledWith(
        'notification:catch-up',
        expect.anything(),
      );
    });

    it('skips catch-up when findByIds returns empty (notifications.length===0)', async () => {
      mockRegistryUnit.drainOfflineQueue.mockResolvedValue(['notif-gone']);
      mockRepoUnit.findByIds.mockResolvedValue([]);

      const client = makeSocket();
      client.handshake = { auth: { token: 'valid-token' }, query: {} } as any;

      const jwt = require('jsonwebtoken') as typeof import('jsonwebtoken');
      jest
        .spyOn(jwt, 'verify')
        .mockImplementationOnce((_t, _g, _o, cb: any) => {
          cb(null, { sub: 'test-user-id' });
        });

      await gateway.handleConnection(client);

      expect(client.emit).not.toHaveBeenCalledWith(
        'notification:catch-up',
        expect.anything(),
      );
    });
  });
});
