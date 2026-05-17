import { CountUnreadNotificationsUseCase } from './count-unread-notifications.use-case';
import { MarkAllNotificationsReadUseCase } from './mark-all-notifications-read.use-case';
import { MarkNotificationReadUseCase } from './mark-notification-read.use-case';
import { Notification } from '../domain/notification';
import { NotificationStatus } from '../domain/notification-status.enum';
import { NotificationType } from '../domain/notification-type.enum';
import {
  NotificationRepository,
  PageResult,
} from '../domain/notification.repository';

const notification = new Notification({
  id: 'n1',
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

function buildRepository(
  overrides: Partial<NotificationRepository> = {},
): NotificationRepository {
  const emptyPage: PageResult<Notification> = {
    content: [],
    totalElements: 0,
    totalPages: 0,
    number: 0,
    size: 30,
    first: true,
    last: true,
  };
  return {
    save: (n) => Promise.resolve(n),
    findByUserId: () => Promise.resolve([]),
    findByUserIdPaged: () => Promise.resolve(emptyPage),
    findById: () => Promise.resolve(null),
    markSent: () => Promise.resolve(),
    markRead: () => Promise.resolve(null),
    markAllRead: () => Promise.resolve(0),
    countUnread: () => Promise.resolve(0),
    ...overrides,
  };
}

describe('Notification read/unread use cases', () => {
  it('MarkNotificationReadUseCase delegates to repository.markRead', async () => {
    const calls: { id: string; userId: string }[] = [];
    const repository = buildRepository({
      markRead: (id, userId) => {
        calls.push({ id, userId });
        return Promise.resolve(notification);
      },
    });
    const useCase = new MarkNotificationReadUseCase(repository);

    await expect(useCase.execute('n1', 'user-1')).resolves.toBe(notification);
    expect(calls).toEqual([{ id: 'n1', userId: 'user-1' }]);
  });

  it('MarkNotificationReadUseCase returns null when repository finds nothing', async () => {
    const repository = buildRepository({
      markRead: () => Promise.resolve(null),
    });
    const useCase = new MarkNotificationReadUseCase(repository);

    await expect(useCase.execute('missing', 'user-1')).resolves.toBeNull();
  });

  it('MarkAllNotificationsReadUseCase returns the affected count', async () => {
    const repository = buildRepository({
      markAllRead: (userId) => Promise.resolve(userId === 'user-1' ? 4 : 0),
    });
    const useCase = new MarkAllNotificationsReadUseCase(repository);

    await expect(useCase.execute('user-1')).resolves.toBe(4);
    await expect(useCase.execute('other')).resolves.toBe(0);
  });

  it('CountUnreadNotificationsUseCase returns the unread count', async () => {
    const repository = buildRepository({
      countUnread: (userId) => Promise.resolve(userId === 'user-1' ? 7 : 0),
    });
    const useCase = new CountUnreadNotificationsUseCase(repository);

    await expect(useCase.execute('user-1')).resolves.toBe(7);
    await expect(useCase.execute('other')).resolves.toBe(0);
  });
});
