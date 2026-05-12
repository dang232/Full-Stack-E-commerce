import { KafkaNotificationConsumer } from './kafka-notification.consumer';
import {
  SendNotificationInput,
  SendNotificationUseCase,
} from './send-notification.use-case';
import { NotificationType } from '../domain/notification-type.enum';

describe('KafkaNotificationConsumer', () => {
  let sentInputs: SendNotificationInput[];
  let consumer: KafkaNotificationConsumer;

  beforeEach(() => {
    sentInputs = [];
    const sendNotificationUseCase = {
      send: (input: SendNotificationInput) => {
        sentInputs.push(input);
        return Promise.resolve(undefined);
      },
    } as unknown as SendNotificationUseCase;
    consumer = new KafkaNotificationConsumer(sendNotificationUseCase);
  });

  it('notifies buyer and seller on order created', async () => {
    await consumer.handleOrderCreated({
      orderId: 'order-1',
      buyerId: 'buyer-1',
      sellerId: 'seller-1',
      status: 'CREATED',
    });

    expect(sentInputs).toHaveLength(2);
    expect(sentInputs.map((input) => input.userId)).toEqual([
      'buyer-1',
      'seller-1',
    ]);
    expect(sentInputs[0]).toMatchObject({
      type: NotificationType.ORDER_CREATED,
      title: 'Order created',
      body: 'Your order has been created.',
      data: { orderId: 'order-1', shipmentId: undefined, status: 'CREATED' },
    });
  });

  it('notifies only present participants', async () => {
    await consumer.handleOrderCancelled({ buyerId: 'buyer-1' });

    expect(sentInputs).toHaveLength(1);
    expect(sentInputs[0]).toMatchObject({
      type: NotificationType.ORDER_CANCELLED,
      userId: 'buyer-1',
      title: 'Order cancelled',
      body: 'An order has been cancelled.',
    });
  });

  it('maps shipped and shipment updated events', async () => {
    await consumer.handleOrderShipped({ sellerId: 'seller-1' });
    await consumer.handleShipmentUpdated({
      buyerId: 'buyer-2',
      shipmentId: 'shipment-1',
      status: 'IN_TRANSIT',
    });

    expect(sentInputs).toEqual([
      expect.objectContaining({
        type: NotificationType.ORDER_SHIPPED,
        userId: 'seller-1',
        title: 'Order shipped',
        body: 'Your order has shipped.',
      }),
      expect.objectContaining({
        type: NotificationType.ORDER_SHIPPED,
        userId: 'buyer-2',
        title: 'Shipment updated',
        body: 'Your shipment status has changed.',
        data: {
          orderId: undefined,
          shipmentId: 'shipment-1',
          status: 'IN_TRANSIT',
        },
      }),
    ]);
  });

  it('does nothing when event has no recipients', async () => {
    await consumer.handleOrderCreated({ orderId: 'order-1' });

    expect(sentInputs).toEqual([]);
  });
});
