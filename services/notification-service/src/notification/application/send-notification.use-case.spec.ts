import {
  SendNotificationUseCase,
  NotificationChannel,
} from './send-notification.use-case';
import { NotificationType } from '../domain/notification-type.enum';
import { NotificationStatus } from '../domain/notification-status.enum';
import { Notification } from '../domain/notification';
import { NotificationRepository } from '../domain/notification.repository';

describe('SendNotificationUseCase', () => {
  let savedNotification: Notification;
  let markedSentId: string | null;

  const mockRepository: NotificationRepository = {
    save: (notification: Notification) => {
      savedNotification = notification;
      return Promise.resolve(notification);
    },
    findByUserId: () => Promise.resolve([]),
    findByUserIdPaged: () =>
      Promise.resolve({
        content: [],
        totalElements: 0,
        totalPages: 0,
        number: 0,
        size: 30,
        first: true,
        last: true,
      }),
    findById: () => Promise.resolve(null),
    markSent: (id: string) => {
      markedSentId = id;
      return Promise.resolve();
    },
    markRead: () => Promise.resolve(null),
    markAllRead: () => Promise.resolve(0),
    countUnread: () => Promise.resolve(0),
  };

  const createChannel = (channelName: string): NotificationChannel => ({
    name: channelName,
    send: () => Promise.resolve(),
  });

  beforeEach(() => {
    savedNotification = undefined as unknown as Notification;
    markedSentId = null;
  });

  it('persists and sends through matching channels', async () => {
    const consoleChannel = createChannel('console');
    const useCase = new SendNotificationUseCase(mockRepository, [
      consoleChannel,
    ]);

    const result = await useCase.send({
      type: NotificationType.ORDER_CREATED,
      userId: 'user-1',
      title: 'Order created',
      body: 'Your order was created.',
    });

    expect(savedNotification).toBeDefined();
    expect(savedNotification.userId).toBe('user-1');
    expect(savedNotification.type).toBe(NotificationType.ORDER_CREATED);
    expect(result.status).toBe(NotificationStatus.SENT);
    expect(markedSentId).toBe(savedNotification.id);
  });

  it('uses all channel names when input channels omitted', async () => {
    const ch1 = createChannel('email');
    const ch2 = createChannel('console');
    const useCase = new SendNotificationUseCase(mockRepository, [ch1, ch2]);

    await useCase.send({
      type: NotificationType.ORDER_CANCELLED,
      userId: 'user-2',
      title: 'Cancelled',
      body: 'Order cancelled.',
    });

    expect(savedNotification.channels).toEqual(['email', 'console']);
  });

  it('uses provided channels instead of defaults', async () => {
    const ch1 = createChannel('email');
    const useCase = new SendNotificationUseCase(mockRepository, [ch1]);

    await useCase.send({
      type: NotificationType.ORDER_SHIPPED,
      userId: 'user-3',
      title: 'Shipped',
      body: 'Order shipped.',
      channels: ['sms'],
    });

    expect(savedNotification.channels).toEqual(['sms']);
  });

  it('skips channels not in notification channel list', async () => {
    let sendCalled = false;
    const matchingChannel: NotificationChannel = {
      name: 'email',
      send: () => {
        sendCalled = true;
        return Promise.resolve();
      },
    };
    const useCase = new SendNotificationUseCase(mockRepository, [
      matchingChannel,
    ]);

    await useCase.send({
      type: NotificationType.PAYMENT_COMPLETED,
      userId: 'user-4',
      title: 'Paid',
      body: 'Payment done.',
      channels: ['email'],
    });

    expect(sendCalled).toBe(true);
  });

  it('defaults data to empty object', async () => {
    const useCase = new SendNotificationUseCase(mockRepository, []);

    await useCase.send({
      type: NotificationType.ORDER_CREATED,
      userId: 'user-5',
      title: 'T',
      body: 'B',
    });

    expect(savedNotification.data).toEqual({});
  });
});
