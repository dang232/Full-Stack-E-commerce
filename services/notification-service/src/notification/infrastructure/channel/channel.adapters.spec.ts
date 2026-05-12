import { Logger } from '@nestjs/common';
import { ConsoleChannelAdapter } from './console-channel.adapter';
import { EmailChannelAdapter } from './email-channel.adapter';
import { Notification } from '../../domain/notification';
import { NotificationStatus } from '../../domain/notification-status.enum';
import { NotificationType } from '../../domain/notification-type.enum';

const notification = new Notification({
  id: 'notification-1',
  userId: 'user-1',
  type: NotificationType.ORDER_CREATED,
  title: 'Order created',
  body: 'Body',
  data: {},
  channels: ['console'],
  status: NotificationStatus.PENDING,
  createdAt: new Date('2026-05-12T00:00:00.000Z'),
});

describe('Channel adapters', () => {
  it('console channel logs notification details', async () => {
    const logSpy = jest.spyOn(Logger.prototype, 'log').mockImplementation();
    const adapter = new ConsoleChannelAdapter();

    await adapter.send(notification);

    expect(adapter.name).toBe('console');
    expect(logSpy).toHaveBeenCalledWith(
      'ORDER_CREATED notification for user-1: Order created',
    );
    logSpy.mockRestore();
  });

  it('email channel resolves without external provider', async () => {
    const adapter = new EmailChannelAdapter();

    await expect(adapter.send(notification)).resolves.toBeUndefined();
    expect(adapter.name).toBe('email');
  });
});
