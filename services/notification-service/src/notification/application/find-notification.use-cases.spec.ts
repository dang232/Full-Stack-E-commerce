import { FindNotificationByIdUseCase } from './find-notification-by-id.use-case';
import { FindUserNotificationsUseCase } from './find-user-notifications.use-case';
import { Notification } from '../domain/notification';
import { NotificationStatus } from '../domain/notification-status.enum';
import { NotificationType } from '../domain/notification-type.enum';
import {
  NotificationRepository,
  PageQuery,
  PageResult,
} from '../domain/notification.repository';

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

describe('Find notification use cases', () => {
  const repository: NotificationRepository = {
    save: (item) => Promise.resolve(item),
    findByUserId: (userId) =>
      Promise.resolve(userId === 'user-1' ? [notification] : []),
    findByUserIdPaged: (userId: string, page: PageQuery) => {
      const items = userId === 'user-1' ? [notification] : [];
      const result: PageResult<Notification> = {
        content: items,
        totalElements: items.length,
        totalPages: items.length === 0 ? 0 : 1,
        number: page.page,
        size: page.size,
        first: page.page === 0,
        last: true,
      };
      return Promise.resolve(result);
    },
    findById: (id) =>
      Promise.resolve(id === 'notification-1' ? notification : null),
    markSent: () => Promise.resolve(),
    markRead: (id, userId) =>
      Promise.resolve(
        id === 'notification-1' && userId === 'user-1' ? notification : null,
      ),
    markAllRead: (userId) => Promise.resolve(userId === 'user-1' ? 1 : 0),
    countUnread: (userId) => Promise.resolve(userId === 'user-1' ? 1 : 0),
  };

  it('finds notifications by user id', async () => {
    const useCase = new FindUserNotificationsUseCase(repository);

    await expect(useCase.execute('user-1')).resolves.toEqual([notification]);
    await expect(useCase.execute('missing')).resolves.toEqual([]);
  });

  it('finds paged notifications by user id with safe defaults', async () => {
    const useCase = new FindUserNotificationsUseCase(repository);

    const page = await useCase.executePaged('user-1', 0, 10);
    expect(page.content).toEqual([notification]);
    expect(page.size).toBe(10);
    expect(page.number).toBe(0);

    const negativePage = await useCase.executePaged('user-1', -1, 10);
    expect(negativePage.number).toBe(0);

    const oversizedPage = await useCase.executePaged('user-1', 0, 5000);
    expect(oversizedPage.size).toBe(100);
  });

  it('finds notification by id', async () => {
    const useCase = new FindNotificationByIdUseCase(repository);

    await expect(useCase.execute('notification-1')).resolves.toBe(notification);
    await expect(useCase.execute('missing')).resolves.toBeNull();
  });
});
