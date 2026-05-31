import { Test } from '@nestjs/testing';
import { KafkaEventConsumer } from '../kafka-event.consumer';
import { SendNotificationUseCase } from '../../../application/command/send-notification.use-case';
import { NotificationType } from '../../../domain/model/notification-type.enum';
import { Priority } from '../../../domain/model/priority.enum';

describe('KafkaEventConsumer', () => {
  let consumer: KafkaEventConsumer;
  const mockSendNotification = {
    execute: jest.fn().mockResolvedValue({ id: 'n1' }),
  };

  beforeEach(async () => {
    jest.clearAllMocks();
    const module = await Test.createTestingModule({
      providers: [
        KafkaEventConsumer,
        { provide: SendNotificationUseCase, useValue: mockSendNotification },
      ],
    }).compile();
    consumer = module.get(KafkaEventConsumer);
  });

  it('order.created sends to both buyer and seller', async () => {
    await consumer.handleOrderCreated({
      orderId: 'ORD-1',
      buyerId: 'buyer-1',
      sellerId: 'seller-1',
    });

    expect(mockSendNotification.execute).toHaveBeenCalledTimes(2);
    expect(mockSendNotification.execute).toHaveBeenCalledWith(
      expect.objectContaining({
        userId: 'buyer-1',
        type: NotificationType.ORDER_CREATED,
        threadId: 'order:ORD-1',
      }),
    );
    expect(mockSendNotification.execute).toHaveBeenCalledWith(
      expect.objectContaining({
        userId: 'seller-1',
        type: NotificationType.SELLER_NEW_ORDER,
        threadId: 'seller-order:ORD-1',
      }),
    );
  });

  it('order.shipped sends to buyer only', async () => {
    await consumer.handleOrderShipped({ orderId: 'ORD-2', buyerId: 'buyer-2' });

    expect(mockSendNotification.execute).toHaveBeenCalledTimes(1);
    expect(mockSendNotification.execute).toHaveBeenCalledWith(
      expect.objectContaining({
        userId: 'buyer-2',
        type: NotificationType.ORDER_SHIPPED,
        title: 'Đơn hàng đang vận chuyển',
        priority: Priority.HIGH,
        idempotencyKey: 'order.shipped:ORD-2:ORDER_SHIPPED',
      }),
    );
  });

  it('product.approved sends to seller', async () => {
    await consumer.handleProductApproved({
      productId: 'P-1',
      productName: 'Áo thun',
      sellerId: 'seller-1',
    });

    expect(mockSendNotification.execute).toHaveBeenCalledWith(
      expect.objectContaining({
        userId: 'seller-1',
        type: NotificationType.PRODUCT_APPROVED,
        deepLink: '/seller/products/P-1',
        threadId: 'product:P-1',
      }),
    );
  });

  it('payout.completed sends to seller', async () => {
    await consumer.handlePayoutCompleted({
      payoutId: 'PAY-1',
      amount: '5000000',
      sellerId: 'seller-2',
    });

    expect(mockSendNotification.execute).toHaveBeenCalledWith(
      expect.objectContaining({
        userId: 'seller-2',
        type: NotificationType.PAYOUT_COMPLETED,
        body: expect.stringContaining('5000000'),
      }),
    );
  });

  it('skips when recipient is missing', async () => {
    await consumer.handleOrderShipped({ orderId: 'ORD-3' }); // no buyerId
    expect(mockSendNotification.execute).not.toHaveBeenCalled();
  });

  it('does not throw on sendNotification failure', async () => {
    mockSendNotification.execute.mockRejectedValue(new Error('DB down'));
    await expect(
      consumer.handleOrderCreated({
        orderId: 'ORD-4',
        buyerId: 'b1',
        sellerId: 's1',
      }),
    ).resolves.not.toThrow();
  });

  it('order.cancelled sends to buyer only', async () => {
    await consumer.handleOrderCancelled({
      orderId: 'ORD-5',
      buyerId: 'buyer-5',
    });

    expect(mockSendNotification.execute).toHaveBeenCalledTimes(1);
    expect(mockSendNotification.execute).toHaveBeenCalledWith(
      expect.objectContaining({
        userId: 'buyer-5',
        type: NotificationType.ORDER_CANCELLED,
        idempotencyKey: 'order.cancelled:ORD-5:ORDER_CANCELLED',
      }),
    );
  });

  it('return.requested sends to seller', async () => {
    await consumer.handleReturnRequested({
      orderId: 'ORD-6',
      returnId: 'RET-1',
      sellerId: 'seller-6',
    });

    expect(mockSendNotification.execute).toHaveBeenCalledWith(
      expect.objectContaining({
        userId: 'seller-6',
        type: NotificationType.RETURN_REQUESTED,
        threadId: 'return:RET-1',
      }),
    );
  });

  it('review.replied sends to buyer', async () => {
    await consumer.handleReviewReplied({
      reviewId: 'REV-1',
      productId: 'P-2',
      productName: 'Giày thể thao',
      buyerId: 'buyer-7',
    });

    expect(mockSendNotification.execute).toHaveBeenCalledWith(
      expect.objectContaining({
        userId: 'buyer-7',
        type: NotificationType.REVIEW_REPLIED,
        threadId: 'review:REV-1',
      }),
    );
  });
});
