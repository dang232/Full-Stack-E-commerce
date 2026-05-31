import { Test } from '@nestjs/testing';
import { NotificationRestController } from '../notification.controller';
import { FindUserNotificationsUseCase } from '../../../application/query/find-user-notifications.use-case';
import { FindNotificationThreadsUseCase } from '../../../application/query/find-notification-threads.use-case';
import { FindThreadNotificationsUseCase } from '../../../application/query/find-thread-notifications.use-case';
import { CountUnreadUseCase } from '../../../application/query/count-unread.use-case';
import { MarkNotificationReadUseCase } from '../../../application/command/mark-notification-read.use-case';
import { MarkAllReadUseCase } from '../../../application/command/mark-all-read.use-case';
import { SendNotificationUseCase } from '../../../application/command/send-notification.use-case';
import { JwtAuthGuard } from '../../auth/jwt-auth.guard';
import { Notification } from '../../../domain/model/notification';
import { NotificationType } from '../../../domain/model/notification-type.enum';

describe('NotificationRestController', () => {
  let controller: NotificationRestController;

  const mockFindNotifications = {
    execute: jest.fn().mockResolvedValue({ items: [], total: 0, page: 0, limit: 20, totalPages: 0 }),
  };
  const mockFindThreads = {
    execute: jest.fn().mockResolvedValue({ threads: [], total: 0, page: 0, limit: 20, totalPages: 0 }),
  };
  const mockFindThreadNotifs = { execute: jest.fn().mockResolvedValue([]) };
  const mockCountUnread = { execute: jest.fn().mockResolvedValue(5) };
  const mockMarkRead = {
    execute: jest.fn().mockImplementation(() => {
      return Notification.create({ userId: 'u1', type: NotificationType.ORDER_CREATED, title: 'T', body: 'B' });
    }),
  };
  const mockMarkAllRead = { execute: jest.fn().mockResolvedValue(3) };
  const mockSendNotification = {
    execute: jest.fn().mockImplementation(() => {
      return Notification.create({ userId: 'u1', type: NotificationType.ORDER_CREATED, title: 'Test', body: 'Test body' });
    }),
  };

  const mockReq = { user: { sub: 'user-1' } } as any;

  beforeEach(async () => {
    jest.clearAllMocks();
    const module = await Test.createTestingModule({
      controllers: [NotificationRestController],
      providers: [
        { provide: FindUserNotificationsUseCase, useValue: mockFindNotifications },
        { provide: FindNotificationThreadsUseCase, useValue: mockFindThreads },
        { provide: FindThreadNotificationsUseCase, useValue: mockFindThreadNotifs },
        { provide: CountUnreadUseCase, useValue: mockCountUnread },
        { provide: MarkNotificationReadUseCase, useValue: mockMarkRead },
        { provide: MarkAllReadUseCase, useValue: mockMarkAllRead },
        { provide: SendNotificationUseCase, useValue: mockSendNotification },
      ],
    })
      .overrideGuard(JwtAuthGuard)
      .useValue({ canActivate: () => true })
      .compile();

    controller = module.get(NotificationRestController);
  });

  it('GET / returns paginated response', async () => {
    const result = await controller.list(mockReq, undefined, undefined, 0, 20);
    expect(result).toHaveProperty('content');
    expect(result).toHaveProperty('totalElements', 0);
    expect(result).toHaveProperty('totalPages', 0);
    expect(result).toHaveProperty('number', 0);
    expect(result).toHaveProperty('size', 20);
  });

  it('GET / passes type filter to use case', async () => {
    await controller.list(mockReq, 'ORDER_CREATED', undefined, 0, 20);
    expect(mockFindNotifications.execute).toHaveBeenCalledWith(
      expect.objectContaining({ type: NotificationType.ORDER_CREATED }),
    );
  });

  it('GET / ignores invalid type filter', async () => {
    await controller.list(mockReq, 'INVALID_TYPE', undefined, 0, 20);
    expect(mockFindNotifications.execute).toHaveBeenCalledWith(
      expect.objectContaining({ type: undefined }),
    );
  });

  it('GET /unread-count returns count', async () => {
    const result = await controller.unreadCount(mockReq);
    expect(result).toEqual({ count: 5 });
    expect(mockCountUnread.execute).toHaveBeenCalledWith('user-1');
  });

  it('GET /threads returns paginated threads', async () => {
    const result = await controller.threads(mockReq, undefined, 0, 20);
    expect(result).toHaveProperty('content');
    expect(result).toHaveProperty('totalElements');
  });

  it('POST /mark-all-read returns updated count', async () => {
    const result = await controller.markAllNotificationsRead(mockReq);
    expect(result).toEqual({ updated: 3 });
    expect(mockMarkAllRead.execute).toHaveBeenCalledWith('user-1');
  });

  it('POST /:id/read returns notification DTO', async () => {
    const result = await controller.markNotificationRead(mockReq, 'notif-123');
    expect(result).toHaveProperty('id');
    expect(result).toHaveProperty('type', 'ORDER_CREATED');
    expect(mockMarkRead.execute).toHaveBeenCalledWith('notif-123', 'user-1');
  });
});
