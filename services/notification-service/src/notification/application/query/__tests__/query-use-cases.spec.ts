import { Test } from '@nestjs/testing';
import { FindUserNotificationsUseCase } from '../find-user-notifications.use-case';
import { FindNotificationThreadsUseCase } from '../find-notification-threads.use-case';
import { FindThreadNotificationsUseCase } from '../find-thread-notifications.use-case';
import { CountUnreadUseCase } from '../count-unread.use-case';
import { NOTIFICATION_REPOSITORY } from '../../../domain/port/outbound/notification.repository';
import { NotificationType } from '../../../domain/model/notification-type.enum';

describe('Query Use Cases', () => {
  const mockRepo = {
    findByUser: jest.fn().mockResolvedValue({ items: [], total: 0 }),
    findThreadsByUser: jest.fn().mockResolvedValue({ threads: [], total: 0 }),
    findByThread: jest.fn().mockResolvedValue([]),
    countUnread: jest.fn().mockResolvedValue(5),
  };

  let findNotifications: FindUserNotificationsUseCase;
  let findThreads: FindNotificationThreadsUseCase;
  let findThreadNotifs: FindThreadNotificationsUseCase;
  let countUnread: CountUnreadUseCase;

  beforeEach(async () => {
    jest.clearAllMocks();
    const module = await Test.createTestingModule({
      providers: [
        FindUserNotificationsUseCase,
        FindNotificationThreadsUseCase,
        FindThreadNotificationsUseCase,
        CountUnreadUseCase,
        { provide: NOTIFICATION_REPOSITORY, useValue: mockRepo },
      ],
    }).compile();

    findNotifications = module.get(FindUserNotificationsUseCase);
    findThreads = module.get(FindNotificationThreadsUseCase);
    findThreadNotifs = module.get(FindThreadNotificationsUseCase);
    countUnread = module.get(CountUnreadUseCase);
  });

  describe('FindUserNotificationsUseCase', () => {
    it('passes type filter to repository', async () => {
      await findNotifications.execute({ userId: 'u1', type: NotificationType.ORDER_CREATED });
      expect(mockRepo.findByUser).toHaveBeenCalledWith(
        expect.objectContaining({ userId: 'u1', type: NotificationType.ORDER_CREATED }),
      );
    });

    it('caps limit at 100', async () => {
      await findNotifications.execute({ userId: 'u1', limit: 999 });
      expect(mockRepo.findByUser).toHaveBeenCalledWith(
        expect.objectContaining({ limit: 100 }),
      );
    });

    it('defaults page to 0 and limit to 20', async () => {
      await findNotifications.execute({ userId: 'u1' });
      expect(mockRepo.findByUser).toHaveBeenCalledWith(
        expect.objectContaining({ page: 0, limit: 20 }),
      );
    });

    it('calculates totalPages', async () => {
      mockRepo.findByUser.mockResolvedValue({ items: [], total: 45 });
      const result = await findNotifications.execute({ userId: 'u1', limit: 20 });
      expect(result.totalPages).toBe(3);
    });
  });

  describe('FindNotificationThreadsUseCase', () => {
    it('passes type filter and caps limit at 50', async () => {
      await findThreads.execute({ userId: 'u1', type: NotificationType.ORDER_SHIPPED, limit: 100 });
      expect(mockRepo.findThreadsByUser).toHaveBeenCalledWith('u1', 0, 50, NotificationType.ORDER_SHIPPED);
    });
  });

  describe('FindThreadNotificationsUseCase', () => {
    it('delegates to repository', async () => {
      await findThreadNotifs.execute('order:123', 'u1');
      expect(mockRepo.findByThread).toHaveBeenCalledWith('order:123', 'u1');
    });
  });

  describe('CountUnreadUseCase', () => {
    it('returns unread count', async () => {
      const count = await countUnread.execute('u1');
      expect(count).toBe(5);
      expect(mockRepo.countUnread).toHaveBeenCalledWith('u1');
    });
  });
});
