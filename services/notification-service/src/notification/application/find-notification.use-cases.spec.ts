import { FindNotificationByIdUseCase } from './find-notification-by-id.use-case';
import { FindUserNotificationsUseCase } from './find-user-notifications.use-case';
import { Notification } from '../domain/notification';
import { NotificationStatus } from '../domain/notification-status.enum';
import { NotificationType } from '../domain/notification-type.enum';
import { NotificationRepository } from '../domain/notification.repository';

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

describe('Find notification use cases', () => {
  const repository: NotificationRepository = {
    save: (item) => Promise.resolve(item),
    findByUserId: (userId) =>
      Promise.resolve(userId === 'user-1' ? [notification] : []),
    findById: (id) =>
      Promise.resolve(id === 'notification-1' ? notification : null),
    markSent: () => Promise.resolve(),
  };

  it('finds notifications by user id', async () => {
    const useCase = new FindUserNotificationsUseCase(repository);

    await expect(useCase.execute('user-1')).resolves.toEqual([notification]);
    await expect(useCase.execute('missing')).resolves.toEqual([]);
  });

  it('finds notification by id', async () => {
    const useCase = new FindNotificationByIdUseCase(repository);

    await expect(useCase.execute('notification-1')).resolves.toBe(notification);
    await expect(useCase.execute('missing')).resolves.toBeNull();
  });
});
