import { BadRequestException, NotFoundException } from '@nestjs/common';
import { FindNotificationByIdUseCase } from '../application/find-notification-by-id.use-case';
import { FindUserNotificationsUseCase } from '../application/find-user-notifications.use-case';
import { SendNotificationUseCase } from '../application/send-notification.use-case';
import { Notification } from '../domain/notification';
import { NotificationStatus } from '../domain/notification-status.enum';
import { NotificationType } from '../domain/notification-type.enum';
import { NotificationController } from './notification.controller';

interface SuccessResponse<T> {
  success: boolean;
  message: string;
  data: T;
  errorCode: string | null;
  timestamp: string;
}

const notification = new Notification({
  id: 'notification-1',
  userId: 'user-1',
  type: NotificationType.ORDER_CREATED,
  title: 'Title',
  body: 'Body',
  data: {},
  channels: ['email'],
  status: NotificationStatus.PENDING,
  createdAt: new Date('2026-05-12T00:00:00.000Z'),
});

const expectTimestamp = (timestamp: string) => {
  expect(typeof timestamp).toBe('string');
};

describe('NotificationController', () => {
  let controller: NotificationController;

  beforeEach(() => {
    const findUserNotificationsUseCase: Pick<
      FindUserNotificationsUseCase,
      'execute'
    > = {
      execute: (userId: string) =>
        Promise.resolve(userId === 'user-1' ? [notification] : []),
    };

    const findNotificationByIdUseCase: Pick<
      FindNotificationByIdUseCase,
      'execute'
    > = {
      execute: (id: string) =>
        Promise.resolve(id === 'notification-1' ? notification : null),
    };

    const sendNotificationUseCase: Pick<SendNotificationUseCase, 'send'> = {
      send: () => Promise.resolve(notification),
    };

    controller = new NotificationController(
      findUserNotificationsUseCase as FindUserNotificationsUseCase,
      findNotificationByIdUseCase as FindNotificationByIdUseCase,
      sendNotificationUseCase as SendNotificationUseCase,
    );
  });

  it('returns user notifications when userId provided via header', async () => {
    const result: SuccessResponse<Notification[]> =
      await controller.findUserNotifications('user-1', undefined);

    expect(result.success).toBe(true);
    expect(result.message).toBe('Success');
    expect(result.data).toEqual([notification]);
    expect(result.errorCode).toBeNull();
    expectTimestamp(result.timestamp);
  });

  it('returns user notifications when userId provided via query', async () => {
    const result: SuccessResponse<Notification[]> =
      await controller.findUserNotifications(undefined, 'user-1');

    expect(result.success).toBe(true);
    expect(result.message).toBe('Success');
    expect(result.data).toEqual([notification]);
    expect(result.errorCode).toBeNull();
    expectTimestamp(result.timestamp);
  });

  it('throws BadRequestException when userId missing', async () => {
    await expect(
      controller.findUserNotifications(undefined, undefined),
    ).rejects.toThrow(BadRequestException);
  });

  it('returns notification by id', async () => {
    const result: SuccessResponse<Notification> =
      await controller.findById('notification-1');

    expect(result.success).toBe(true);
    expect(result.message).toBe('Success');
    expect(result.data).toBe(notification);
    expect(result.errorCode).toBeNull();
    expectTimestamp(result.timestamp);
  });

  it('throws NotFoundException when notification missing', async () => {
    await expect(controller.findById('missing')).rejects.toThrow(
      NotFoundException,
    );
  });

  it('creates test notification for userId via header', async () => {
    const result: SuccessResponse<Notification> =
      await controller.createTestNotification('user-1', undefined);

    expect(result.success).toBe(true);
    expect(result.message).toBe('Success');
    expect(result.data).toBe(notification);
    expect(result.errorCode).toBeNull();
    expectTimestamp(result.timestamp);
  });

  it('creates test notification for userId via query', async () => {
    const result: SuccessResponse<Notification> =
      await controller.createTestNotification(undefined, 'user-1');

    expect(result.success).toBe(true);
    expect(result.message).toBe('Success');
    expect(result.data).toBe(notification);
    expect(result.errorCode).toBeNull();
    expectTimestamp(result.timestamp);
  });

  it('throws BadRequestException for test notification when userId missing', async () => {
    await expect(
      controller.createTestNotification(undefined, undefined),
    ).rejects.toThrow(BadRequestException);
  });
});
