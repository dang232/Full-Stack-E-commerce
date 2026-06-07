import { SocketioRealtimeChannelAdapter } from '../socketio-realtime-channel.adapter';
import { Notification } from '../../../domain/model/notification';
import { NotificationType } from '../../../domain/model/notification-type.enum';

describe('SocketioRealtimeChannelAdapter', () => {
  const mockEmit = jest.fn();
  const mockTo = jest.fn().mockReturnValue({ emit: mockEmit });
  const mockGateway = { server: { to: mockTo } } as never;

  let adapter: SocketioRealtimeChannelAdapter;

  beforeEach(() => {
    jest.clearAllMocks();
    adapter = new SocketioRealtimeChannelAdapter(mockGateway);
  });

  it('sends notification to user room', async () => {
    const notification = Notification.create({
      userId: 'user-1',
      type: NotificationType.ORDER_CREATED,
      title: 'Test',
      body: 'Body',
      deepLink: '/orders/123',
    });

    await adapter.sendToUser('user-1', notification);

    expect(mockTo).toHaveBeenCalledWith('user:user-1');
    expect(mockEmit).toHaveBeenCalledWith(
      'notification:new',
      expect.objectContaining({
        id: notification.id,
        type: 'ORDER_CREATED',
        title: 'Test',
        body: 'Body',
        deepLink: '/orders/123',
      }),
    );
  });

  it('sends batch to user room', async () => {
    const n1 = Notification.create({
      userId: 'u1',
      type: NotificationType.ORDER_CREATED,
      title: 'T1',
      body: 'B1',
    });
    const n2 = Notification.create({
      userId: 'u1',
      type: NotificationType.ORDER_SHIPPED,
      title: 'T2',
      body: 'B2',
    });

    await adapter.sendBatchToUser('u1', [n1, n2]);

    expect(mockTo).toHaveBeenCalledWith('user:u1');
    expect(mockEmit).toHaveBeenCalledWith(
      'notification:catch-up',
      expect.arrayContaining([
        expect.objectContaining({ title: 'T1' }),
        expect.objectContaining({ title: 'T2' }),
      ]),
    );
  });

  it('includes null threadId and threadTitle when no thread', async () => {
    const notification = Notification.create({
      userId: 'u1',
      type: NotificationType.PAYMENT_COMPLETED,
      title: 'Paid',
      body: 'Payment done',
    });

    await adapter.sendToUser('u1', notification);

    expect(mockEmit).toHaveBeenCalledWith(
      'notification:new',
      expect.objectContaining({
        threadId: null,
        threadTitle: null,
      }),
    );
  });

  it('serializes createdAt as ISO string', async () => {
    const notification = Notification.create({
      userId: 'u1',
      type: NotificationType.ORDER_DELIVERED,
      title: 'Delivered',
      body: 'Your order arrived',
    });

    await adapter.sendToUser('u1', notification);

    // eslint-disable-next-line @typescript-eslint/no-unsafe-member-access
    const payload = mockEmit.mock.calls[0][1] as Record<string, unknown>;
    expect(typeof payload['createdAt']).toBe('string');
    expect(() => new Date(payload['createdAt'] as string)).not.toThrow();
  });
});
