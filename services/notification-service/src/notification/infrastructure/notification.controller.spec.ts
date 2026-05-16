import { BadRequestException, NotFoundException } from '@nestjs/common';
import { CountUnreadNotificationsUseCase } from '../application/count-unread-notifications.use-case';
import { FindNotificationByIdUseCase } from '../application/find-notification-by-id.use-case';
import { FindUserNotificationsUseCase } from '../application/find-user-notifications.use-case';
import { MarkAllNotificationsReadUseCase } from '../application/mark-all-notifications-read.use-case';
import { MarkNotificationReadUseCase } from '../application/mark-notification-read.use-case';
import { SendNotificationUseCase } from '../application/send-notification.use-case';
import { Notification } from '../domain/notification';
import { NotificationStatus } from '../domain/notification-status.enum';
import { NotificationType } from '../domain/notification-type.enum';
import { PageResult } from '../domain/notification.repository';
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
  read: false,
  readAt: null,
  createdAt: new Date('2026-05-12T00:00:00.000Z'),
});

const expectTimestamp = (timestamp: string) => {
  expect(typeof timestamp).toBe('string');
};

const buildPage = (items: Notification[]): PageResult<Notification> => ({
  content: items,
  totalElements: items.length,
  totalPages: 1,
  number: 0,
  size: 30,
  first: true,
  last: true,
});

describe('NotificationController', () => {
  let controller: NotificationController;
  let executePagedMock: jest.Mock;
  let markReadMock: jest.Mock;
  let markAllReadMock: jest.Mock;
  let countUnreadMock: jest.Mock;

  beforeEach(() => {
    executePagedMock = jest.fn(
      (userId: string): Promise<PageResult<Notification>> =>
        Promise.resolve(buildPage(userId === 'user-1' ? [notification] : [])),
    );
    markReadMock = jest.fn(
      (id: string, userId: string): Promise<Notification | null> =>
        Promise.resolve(
          id === 'notification-1' && userId === 'user-1' ? notification : null,
        ),
    );
    markAllReadMock = jest.fn(
      (userId: string): Promise<number> =>
        Promise.resolve(userId === 'user-1' ? 3 : 0),
    );
    countUnreadMock = jest.fn(
      (userId: string): Promise<number> =>
        Promise.resolve(userId === 'user-1' ? 2 : 0),
    );

    const findUserNotificationsUseCase: Pick<
      FindUserNotificationsUseCase,
      'execute' | 'executePaged'
    > = {
      execute: (userId: string) =>
        Promise.resolve(userId === 'user-1' ? [notification] : []),
      executePaged: executePagedMock,
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

    const markNotificationReadUseCase: Pick<
      MarkNotificationReadUseCase,
      'execute'
    > = {
      execute: markReadMock,
    };

    const markAllNotificationsReadUseCase: Pick<
      MarkAllNotificationsReadUseCase,
      'execute'
    > = {
      execute: markAllReadMock,
    };

    const countUnreadNotificationsUseCase: Pick<
      CountUnreadNotificationsUseCase,
      'execute'
    > = {
      execute: countUnreadMock,
    };

    controller = new NotificationController(
      findUserNotificationsUseCase as FindUserNotificationsUseCase,
      findNotificationByIdUseCase as FindNotificationByIdUseCase,
      sendNotificationUseCase as SendNotificationUseCase,
      markNotificationReadUseCase as MarkNotificationReadUseCase,
      markAllNotificationsReadUseCase as MarkAllNotificationsReadUseCase,
      countUnreadNotificationsUseCase as CountUnreadNotificationsUseCase,
    );
  });

  it('returns paged user notifications when userId provided via header', async () => {
    const result: SuccessResponse<PageResult<Notification>> =
      await controller.findUserNotifications(
        'user-1',
        undefined,
        undefined,
        undefined,
      );

    expect(result.success).toBe(true);
    expect(result.message).toBe('Success');
    expect(result.data.content).toEqual([notification]);
    expect(result.data.totalElements).toBe(1);
    expect(result.errorCode).toBeNull();
    expect(executePagedMock).toHaveBeenCalledWith('user-1', 0, 30);
    expectTimestamp(result.timestamp);
  });

  it('returns paged user notifications when userId provided via query', async () => {
    const result: SuccessResponse<PageResult<Notification>> =
      await controller.findUserNotifications(undefined, 'user-1', '2', '50');

    expect(result.success).toBe(true);
    expect(result.data.content).toEqual([notification]);
    expect(executePagedMock).toHaveBeenCalledWith('user-1', 2, 50);
    expectTimestamp(result.timestamp);
  });

  it('throws BadRequestException when userId missing on list', async () => {
    await expect(
      controller.findUserNotifications(
        undefined,
        undefined,
        undefined,
        undefined,
      ),
    ).rejects.toThrow(BadRequestException);
  });

  it('returns notification by id', async () => {
    const result: SuccessResponse<Notification> =
      await controller.findById('notification-1');

    expect(result.success).toBe(true);
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
    expect(result.data).toBe(notification);
    expectTimestamp(result.timestamp);
  });

  it('creates test notification for userId via query', async () => {
    const result: SuccessResponse<Notification> =
      await controller.createTestNotification(undefined, 'user-1');

    expect(result.success).toBe(true);
    expect(result.data).toBe(notification);
    expectTimestamp(result.timestamp);
  });

  it('throws BadRequestException for test notification when userId missing', async () => {
    await expect(
      controller.createTestNotification(undefined, undefined),
    ).rejects.toThrow(BadRequestException);
  });

  it('marks a notification as read when caller owns it', async () => {
    const result: SuccessResponse<Notification> = await controller.markRead(
      'user-1',
      undefined,
      'notification-1',
    );

    expect(result.success).toBe(true);
    expect(result.data).toBe(notification);
    expect(markReadMock).toHaveBeenCalledWith('notification-1', 'user-1');
  });

  it('throws NotFoundException when marking a notification the caller does not own', async () => {
    await expect(
      controller.markRead('other-user', undefined, 'notification-1'),
    ).rejects.toThrow(NotFoundException);
  });

  it('throws BadRequestException when marking read with no userId', async () => {
    await expect(
      controller.markRead(undefined, undefined, 'notification-1'),
    ).rejects.toThrow(BadRequestException);
  });

  it('marks all notifications read for the caller', async () => {
    const result: SuccessResponse<{ updated: number }> =
      await controller.markAllRead('user-1', undefined);

    expect(result.success).toBe(true);
    expect(result.data.updated).toBe(3);
    expect(markAllReadMock).toHaveBeenCalledWith('user-1');
  });

  it('returns the unread count for the caller', async () => {
    const result: SuccessResponse<{ count: number }> =
      await controller.unreadCount('user-1', undefined);

    expect(result.success).toBe(true);
    expect(result.data.count).toBe(2);
    expect(countUnreadMock).toHaveBeenCalledWith('user-1');
  });

  it('throws BadRequestException when unread-count called with no userId', async () => {
    await expect(controller.unreadCount(undefined, undefined)).rejects.toThrow(
      BadRequestException,
    );
  });
});
