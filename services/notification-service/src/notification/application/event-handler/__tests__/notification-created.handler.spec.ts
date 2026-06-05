import { Test } from '@nestjs/testing';
import { NotificationCreatedHandler } from '../notification-created.handler';
import { NOTIFICATION_REPOSITORY } from '../../../domain/port/outbound/notification.repository';
import { REALTIME_CHANNEL_PORT } from '../../../domain/port/outbound/realtime-channel.port';
import { CONNECTION_REGISTRY_PORT } from '../../../domain/port/outbound/connection-registry.port';
import { NOTIFICATION_PREFERENCES_REPOSITORY } from '../../../domain/port/outbound/notification-preferences.repository';
import { EMAIL_CHANNEL_PORT } from '../../../domain/port/outbound/email-channel.port';
import { PUSH_CHANNEL_PORT } from '../../../domain/port/outbound/push-channel.port';
import { Notification } from '../../../domain/model/notification';
import { NotificationType } from '../../../domain/model/notification-type.enum';
import { NotificationCreatedEvent } from '../../../domain/event/notification-created.event';

describe('NotificationCreatedHandler', () => {
  let handler: NotificationCreatedHandler;
  const mockRepo = {
    findById: jest.fn(),
    save: jest.fn().mockResolvedValue(undefined),
  };
  const mockChannel = {
    sendToUser: jest.fn().mockResolvedValue(undefined),
    sendBatchToUser: jest.fn().mockResolvedValue(undefined),
  };
  const mockRegistry = {
    isOnline: jest.fn(),
    enqueueOffline: jest.fn().mockResolvedValue(undefined),
  };
  const mockPrefsRepo = {
    findByUserId: jest.fn().mockResolvedValue(null),
    save: jest.fn().mockResolvedValue(undefined),
  };
  const mockEmailChannel = {
    send: jest.fn().mockResolvedValue(true),
    isEnabled: jest.fn().mockReturnValue(false),
  };
  const mockPushChannel = {
    send: jest.fn().mockResolvedValue(true),
    isEnabled: jest.fn().mockReturnValue(false),
  };

  beforeEach(async () => {
    jest.clearAllMocks();
    const module = await Test.createTestingModule({
      providers: [
        NotificationCreatedHandler,
        { provide: NOTIFICATION_REPOSITORY, useValue: mockRepo },
        { provide: REALTIME_CHANNEL_PORT, useValue: mockChannel },
        { provide: CONNECTION_REGISTRY_PORT, useValue: mockRegistry },
        { provide: NOTIFICATION_PREFERENCES_REPOSITORY, useValue: mockPrefsRepo },
        { provide: EMAIL_CHANNEL_PORT, useValue: mockEmailChannel },
        { provide: PUSH_CHANNEL_PORT, useValue: mockPushChannel },
      ],
    }).compile();
    handler = module.get(NotificationCreatedHandler);
  });

  it('delivers via realtime channel when user is online', async () => {
    const notification = Notification.create({
      userId: 'user-1',
      type: NotificationType.ORDER_CREATED,
      title: 'T',
      body: 'B',
    });
    mockRepo.findById.mockResolvedValue(notification);
    mockRegistry.isOnline.mockResolvedValue(true);

    await handler.handle(
      new NotificationCreatedEvent(notification.id, 'user-1', NotificationType.ORDER_CREATED),
    );

    expect(mockChannel.sendToUser).toHaveBeenCalledWith('user-1', notification);
    expect(mockRepo.save).toHaveBeenCalled();
  });

  it('enqueues offline when user is not connected', async () => {
    const notification = Notification.create({
      userId: 'user-2',
      type: NotificationType.ORDER_SHIPPED,
      title: 'T',
      body: 'B',
    });
    mockRepo.findById.mockResolvedValue(notification);
    mockRegistry.isOnline.mockResolvedValue(false);

    await handler.handle(
      new NotificationCreatedEvent(notification.id, 'user-2', NotificationType.ORDER_SHIPPED),
    );

    expect(mockChannel.sendToUser).not.toHaveBeenCalled();
    expect(mockRegistry.enqueueOffline).toHaveBeenCalledWith('user-2', notification.id);
  });

  it('marks failed and enqueues offline on delivery error', async () => {
    const notification = Notification.create({
      userId: 'user-3',
      type: NotificationType.PAYMENT_COMPLETED,
      title: 'T',
      body: 'B',
    });
    mockRepo.findById.mockResolvedValue(notification);
    mockRegistry.isOnline.mockResolvedValue(true);
    mockChannel.sendToUser.mockRejectedValue(new Error('Socket timeout'));

    await handler.handle(
      new NotificationCreatedEvent(notification.id, 'user-3', NotificationType.PAYMENT_COMPLETED),
    );

    expect(mockRegistry.enqueueOffline).toHaveBeenCalledWith('user-3', notification.id);
    expect(mockRepo.save).toHaveBeenCalled();
  });

  it('dispatches email when channel is enabled and recipientEmail is provided', async () => {
    const notification = Notification.create({
      userId: 'user-4',
      type: NotificationType.ORDER_CREATED,
      title: 'T',
      body: 'B',
    });
    mockRepo.findById.mockResolvedValue(notification);
    mockRegistry.isOnline.mockResolvedValue(false);
    mockEmailChannel.isEnabled.mockReturnValue(true);

    await handler.handle(
      new NotificationCreatedEvent(
        notification.id,
        'user-4',
        NotificationType.ORDER_CREATED,
        [],
        'user4@example.com',
      ),
    );

    expect(mockEmailChannel.send).toHaveBeenCalledWith(
      { userId: 'user-4', email: 'user4@example.com' },
      notification,
    );
  });

  it('skips email dispatch when recipientEmail is absent', async () => {
    const notification = Notification.create({
      userId: 'user-5',
      type: NotificationType.ORDER_CREATED,
      title: 'T',
      body: 'B',
    });
    mockRepo.findById.mockResolvedValue(notification);
    mockRegistry.isOnline.mockResolvedValue(false);
    mockEmailChannel.isEnabled.mockReturnValue(true);

    await handler.handle(
      new NotificationCreatedEvent(notification.id, 'user-5', NotificationType.ORDER_CREATED),
    );

    expect(mockEmailChannel.send).not.toHaveBeenCalled();
  });

  it('dispatches push when channel is enabled and deviceToken is provided', async () => {
    const notification = Notification.create({
      userId: 'user-6',
      type: NotificationType.ORDER_SHIPPED,
      title: 'Shipped',
      body: 'On the way',
    });
    mockRepo.findById.mockResolvedValue(notification);
    mockRegistry.isOnline.mockResolvedValue(false);
    mockPushChannel.isEnabled.mockReturnValue(true);

    await handler.handle(
      new NotificationCreatedEvent(
        notification.id,
        'user-6',
        NotificationType.ORDER_SHIPPED,
        [],
        undefined,
        'device-token-abc',
      ),
    );

    expect(mockPushChannel.send).toHaveBeenCalledWith(
      { userId: 'user-6', deviceToken: 'device-token-abc' },
      'Shipped',
      'On the way',
      undefined,
    );
  });
});
