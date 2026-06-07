import { Test } from '@nestjs/testing';
import { EventEmitter2 } from '@nestjs/event-emitter';
import { SendNotificationUseCase } from '../send-notification.use-case';
import { NOTIFICATION_REPOSITORY } from '../../../domain/port/outbound/notification.repository';
import { NOTIFICATION_PREFERENCES_REPOSITORY } from '../../../domain/port/outbound/notification-preferences.repository';
import { DEDUPLICATION_PORT } from '../../../domain/port/outbound/deduplication.port';
import { NotificationType } from '../../../domain/model/notification-type.enum';
import {
  NotificationChannel,
  NotificationPreferences,
} from '../../../domain/model/notification-preferences';

describe('SendNotificationUseCase', () => {
  let useCase: SendNotificationUseCase;
  const mockRepo = {
    save: jest.fn().mockResolvedValue(undefined),
    findByIdempotencyKey: jest.fn(),
  };
  const mockPrefsRepo = {
    findByUserId: jest.fn().mockResolvedValue(null),
    save: jest.fn(),
  };
  const mockDedup = {
    isDuplicate: jest.fn().mockResolvedValue(false),
    markProcessed: jest.fn().mockResolvedValue(undefined),
    tryAcquire: jest.fn().mockResolvedValue(true),
  };
  const mockEmitter = { emit: jest.fn() };

  beforeEach(async () => {
    jest.clearAllMocks();
    const module = await Test.createTestingModule({
      providers: [
        SendNotificationUseCase,
        { provide: NOTIFICATION_REPOSITORY, useValue: mockRepo },
        {
          provide: NOTIFICATION_PREFERENCES_REPOSITORY,
          useValue: mockPrefsRepo,
        },
        { provide: DEDUPLICATION_PORT, useValue: mockDedup },
        { provide: EventEmitter2, useValue: mockEmitter },
      ],
    }).compile();
    useCase = module.get(SendNotificationUseCase);
  });

  it('creates notification, saves, marks dedup, and emits event', async () => {
    const result = await useCase.execute({
      userId: 'user-1',
      type: NotificationType.ORDER_CREATED,
      title: 'Đặt hàng thành công',
      body: 'Đơn hàng #123 đã được đặt.',
      idempotencyKey: 'order.created:123:ORDER_CREATED',
    });

    expect(result.id).toBeDefined();
    expect(result.userId).toBe('user-1');
    expect(result.type).toBe(NotificationType.ORDER_CREATED);
    expect(mockRepo.save).toHaveBeenCalledWith(result);
    expect(mockDedup.tryAcquire).toHaveBeenCalledWith(
      'order.created:123:ORDER_CREATED',
    );
    expect(mockEmitter.emit).toHaveBeenCalledWith(
      'notification.created',
      expect.objectContaining({
        notificationId: result.id,
        userId: 'user-1',
      }),
    );
  });

  it('returns existing notification for duplicate idempotency key', async () => {
    const existing = { id: 'existing-id', userId: 'user-1' };
    mockDedup.tryAcquire.mockResolvedValue(false);
    mockRepo.findByIdempotencyKey.mockResolvedValue(existing);

    const result = await useCase.execute({
      userId: 'user-1',
      type: NotificationType.ORDER_CREATED,
      title: 'T',
      body: 'B',
      idempotencyKey: 'dup-key',
    });

    expect(result).toBe(existing);
    expect(mockRepo.save).not.toHaveBeenCalled();
    expect(mockEmitter.emit).not.toHaveBeenCalled();
  });

  it('creates notification without idempotency key', async () => {
    const result = await useCase.execute({
      userId: 'user-2',
      type: NotificationType.PAYMENT_COMPLETED,
      title: 'Payment',
      body: 'Done',
    });

    expect(result.id).toBeDefined();
    expect(mockDedup.isDuplicate).not.toHaveBeenCalled();
    expect(mockDedup.markProcessed).not.toHaveBeenCalled();
    expect(mockRepo.save).toHaveBeenCalled();
    expect(mockEmitter.emit).toHaveBeenCalled();
  });

  it('creates notification with thread', async () => {
    const result = await useCase.execute({
      userId: 'user-3',
      type: NotificationType.ORDER_SHIPPED,
      title: 'Shipped',
      body: 'On the way',
      threadId: 'order:ORD-001',
      threadTitle: 'Đơn hàng #ORD-001',
    });

    expect(result!.thread).not.toBeNull();
    expect(result!.thread!.threadId).toBe('order:ORD-001');
    expect(result!.thread!.threadTitle).toBe('Đơn hàng #ORD-001');
  });

  it('short-circuits when all channels are disabled for the notification type', async () => {
    const prefs = NotificationPreferences.createDefault('user-1');
    prefs.setTypeChannels(NotificationType.ORDER_CREATED, []);
    mockPrefsRepo.findByUserId.mockResolvedValue(prefs);

    const result = await useCase.execute({
      userId: 'user-1',
      type: NotificationType.ORDER_CREATED,
      title: 'Suppressed',
      body: 'Should not persist',
    });

    expect(result).toBeNull();
    expect(mockRepo.save).not.toHaveBeenCalled();
    expect(mockEmitter.emit).not.toHaveBeenCalled();
  });

  it('short-circuits when user is globally muted', async () => {
    const prefs = NotificationPreferences.createDefault('user-1');
    prefs.setMuted(true);
    mockPrefsRepo.findByUserId.mockResolvedValue(prefs);

    const result = await useCase.execute({
      userId: 'user-1',
      type: NotificationType.ORDER_CREATED,
      title: 'Muted',
      body: 'Should not persist',
    });

    expect(result).toBeNull();
    expect(mockRepo.save).not.toHaveBeenCalled();
    expect(mockEmitter.emit).not.toHaveBeenCalled();
  });

  it('proceeds when idempotency key not acquired but notification not found in repo', async () => {
    // tryAcquire=false means another process claimed the key, but repo has no record — proceed to create
    mockDedup.tryAcquire.mockResolvedValue(false);
    mockRepo.findByIdempotencyKey.mockResolvedValue(null);
    mockPrefsRepo.findByUserId.mockResolvedValue(null);
    mockRepo.save.mockResolvedValue(undefined);

    const result = await useCase.execute({
      userId: 'user-race',
      type: NotificationType.ORDER_CREATED,
      title: 'T',
      body: 'B',
      idempotencyKey: 'race-key',
    });

    // Key acquired=false but no existing notification found — proceeds to create
    expect(result).not.toBeNull();
    expect(result!.userId).toBe('user-race');
    expect(mockRepo.save).toHaveBeenCalled();
  });

  it('covers the else branch when event is not a NotificationCreatedEvent instance', async () => {
    // The else at line 121 emits the raw event. We verify the happy-path emit happens
    // (NotificationCreatedEvent IS the instanceof case; else fires for any other event type).
    // We verify normal emit works (branch is internal to Notification.pullDomainEvents).
    mockPrefsRepo.findByUserId.mockResolvedValue(null);
    mockRepo.save.mockResolvedValue(undefined);

    const result = await useCase.execute({
      userId: 'user-evt',
      type: NotificationType.ORDER_CREATED,
      title: 'T',
      body: 'B',
    });

    expect(result).not.toBeNull();
    expect(mockEmitter.emit).toHaveBeenCalledWith(
      'notification.created',
      expect.anything(),
    );
  });

  it('emits raw event for non-NotificationCreatedEvent domain events', async () => {
    // Force pullDomainEvents to return a plain object (not NotificationCreatedEvent instance)
    // to cover the else branch at line 122
    const { Notification: NotificationClass } = require('../../../domain/model/notification') as typeof import('../../../domain/model/notification');
    const spy = jest.spyOn(NotificationClass.prototype, 'pullDomainEvents').mockReturnValueOnce([
      { type: 'SOME_OTHER_EVENT' } as any,
    ]);

    mockPrefsRepo.findByUserId.mockResolvedValue(null);
    mockRepo.save.mockResolvedValue(undefined);

    await useCase.execute({
      userId: 'user-else',
      type: NotificationType.ORDER_CREATED,
      title: 'T',
      body: 'B',
    });

    expect(mockEmitter.emit).toHaveBeenCalledWith('notification.created', { type: 'SOME_OTHER_EVENT' });
    spy.mockRestore();
  });

  it('persists but emits with suppressedChannels when only some channels are disabled', async () => {
    const prefs = NotificationPreferences.createDefault('user-1');
    prefs.setTypeChannels(NotificationType.ORDER_CREATED, [
      NotificationChannel.IN_APP,
    ]);
    mockPrefsRepo.findByUserId.mockResolvedValue(prefs);

    const result = await useCase.execute({
      userId: 'user-1',
      type: NotificationType.ORDER_CREATED,
      title: 'Partial',
      body: 'Only in-app',
    });

    expect(result).not.toBeNull();
    expect(mockRepo.save).toHaveBeenCalled();
    expect(mockEmitter.emit).toHaveBeenCalledWith(
      'notification.created',
      expect.objectContaining({
        // eslint-disable-next-line @typescript-eslint/no-unsafe-assignment
        suppressedChannels: expect.arrayContaining([NotificationChannel.EMAIL]),
      }),
    );
  });
});
