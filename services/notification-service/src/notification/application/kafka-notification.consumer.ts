import { Controller } from '@nestjs/common';
import { Ctx, KafkaContext, MessagePattern, Payload } from '@nestjs/microservices';
import { context, propagation, trace } from '@opentelemetry/api';
import { NotificationType } from '../domain/notification-type.enum';
import { SendNotificationUseCase } from './send-notification.use-case';

interface OrderEventPayload {
  orderId?: string;
  buyerId?: string;
  sellerId?: string;
  shipmentId?: string;
  status?: string;
  [key: string]: unknown;
}

@Controller()
export class KafkaNotificationConsumer {
  constructor(
    private readonly sendNotificationUseCase: SendNotificationUseCase,
  ) {}

  @MessagePattern('order.created')
  async handleOrderCreated(
    @Payload() payload: OrderEventPayload,
    @Ctx() kafkaContext: KafkaContext,
  ): Promise<void> {
    await this.runWithKafkaTraceContext(kafkaContext, 'order.created', () =>
      this.notifyOrderParticipants(
        payload,
        NotificationType.ORDER_CREATED,
        'Order created',
        'Your order has been created.',
      ),
    );
  }

  @MessagePattern('order.cancelled')
  async handleOrderCancelled(
    @Payload() payload: OrderEventPayload,
    @Ctx() kafkaContext: KafkaContext,
  ): Promise<void> {
    await this.runWithKafkaTraceContext(kafkaContext, 'order.cancelled', () =>
      this.notifyOrderParticipants(
        payload,
        NotificationType.ORDER_CANCELLED,
        'Order cancelled',
        'An order has been cancelled.',
      ),
    );
  }

  @MessagePattern('order.shipped')
  async handleOrderShipped(
    @Payload() payload: OrderEventPayload,
    @Ctx() kafkaContext: KafkaContext,
  ): Promise<void> {
    await this.runWithKafkaTraceContext(kafkaContext, 'order.shipped', () =>
      this.notifyOrderParticipants(
        payload,
        NotificationType.ORDER_SHIPPED,
        'Order shipped',
        'Your order has shipped.',
      ),
    );
  }

  @MessagePattern('shipment.updated')
  async handleShipmentUpdated(
    @Payload() payload: OrderEventPayload,
    @Ctx() kafkaContext: KafkaContext,
  ): Promise<void> {
    await this.runWithKafkaTraceContext(kafkaContext, 'shipment.updated', () =>
      this.notifyOrderParticipants(
        payload,
        NotificationType.ORDER_SHIPPED,
        'Shipment updated',
        'Your shipment status has changed.',
      ),
    );
  }

  private async runWithKafkaTraceContext(
    kafkaContext: KafkaContext,
    topic: string,
    handler: () => Promise<void>,
  ): Promise<void> {
    const message = kafkaContext.getMessage();
    const extractedContext = propagation.extract(
      context.active(),
      message.headers,
    );

    return trace
      .getTracer('notification-service')
      .startActiveSpan(`kafka.${topic}.notification`, {}, extractedContext, async (span) => {
        try {
          await context.with(extractedContext, handler);
        } finally {
          span.end();
        }
      });
  }

  private async notifyOrderParticipants(
    payload: OrderEventPayload,
    type: NotificationType,
    title: string,
    body: string,
  ): Promise<void> {
    const recipients = [payload.buyerId, payload.sellerId].filter(
      (userId): userId is string => Boolean(userId),
    );

    await Promise.all(
      recipients.map((userId) =>
        this.sendNotificationUseCase.send({
          type,
          userId,
          title,
          body,
          data: {
            orderId: payload.orderId,
            shipmentId: payload.shipmentId,
            status: payload.status,
          },
        }),
      ),
    );
  }
}
