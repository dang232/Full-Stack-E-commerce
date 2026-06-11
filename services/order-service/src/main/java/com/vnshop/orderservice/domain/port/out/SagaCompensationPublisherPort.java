package com.vnshop.orderservice.domain.port.out;

/**
 * Publishes saga compensation request events to downstream services.
 *
 * <p>Compensation requests are published directly to Kafka (not via outbox) so the
 * topic names with hyphens (e.g. {@code inventory.release-requested}) are preserved
 * exactly. The outbox {@code topicFor()} helper only maps underscores to dots, which
 * would produce the wrong topic name for these events.
 */
public interface SagaCompensationPublisherPort {

    /**
     * Publishes {@code inventory.release-requested} so the inventory-service releases
     * the stock reserved for the given order.
     */
    void publishInventoryReleaseRequested(String orderId, String sagaId);

    /**
     * Publishes {@code payment.refund.requested} so the payment-service refunds the
     * charge taken for the given order.
     */
    void publishPaymentRefundRequested(String orderId, String sagaId);
}
