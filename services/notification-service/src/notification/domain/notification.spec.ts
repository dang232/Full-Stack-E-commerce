import { Notification } from './notification';
import { NotificationStatus } from './notification-status.enum';
import { NotificationType } from './notification-type.enum';

describe('Notification', () => {
  it('creates pending notifications with generated metadata', () => {
    const notification = Notification.create({
      userId: 'user-1',
      type: NotificationType.ORDER_CREATED,
      title: 'Order created',
      body: 'Your order has been created.',
      data: { orderId: 'order-1' },
      channels: ['email'],
    });

    expect(notification.id).toEqual(expect.any(String));
    expect(notification.userId).toBe('user-1');
    expect(notification.type).toBe(NotificationType.ORDER_CREATED);
    expect(notification.title).toBe('Order created');
    expect(notification.body).toBe('Your order has been created.');
    expect(notification.data).toEqual({ orderId: 'order-1' });
    expect(notification.channels).toEqual(['email']);
    expect(notification.status).toBe(NotificationStatus.PENDING);
    expect(notification.createdAt).toBeInstanceOf(Date);
  });

  it('preserves constructed properties', () => {
    const createdAt = new Date('2026-05-12T00:00:00.000Z');

    const notification = new Notification({
      id: 'notification-1',
      userId: 'user-2',
      type: NotificationType.PAYMENT_COMPLETED,
      title: 'Payment completed',
      body: 'Payment received.',
      data: {},
      channels: ['console'],
      status: NotificationStatus.SENT,
      createdAt,
    });

    expect(notification).toMatchObject({
      id: 'notification-1',
      userId: 'user-2',
      type: NotificationType.PAYMENT_COMPLETED,
      title: 'Payment completed',
      body: 'Payment received.',
      data: {},
      channels: ['console'],
      status: NotificationStatus.SENT,
      createdAt,
    });
  });
});
