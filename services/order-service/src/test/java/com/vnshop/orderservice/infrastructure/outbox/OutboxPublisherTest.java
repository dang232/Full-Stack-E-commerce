package com.vnshop.orderservice.infrastructure.outbox;

import com.vnshop.orderservice.infrastructure.outbox.OutboxEvent.Status;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxPublisherTest {

    @Test
    void topicForOrderCreated() {
        assertThat(OutboxPublisher.topicFor("ORDER_CREATED"))
                .isEqualTo("order.created");
    }

    @Test
    void topicForOrderCancelled() {
        assertThat(OutboxPublisher.topicFor("ORDER_CANCELLED"))
                .isEqualTo("order.cancelled");
    }

    @Test
    void topicForOrderShipped() {
        assertThat(OutboxPublisher.topicFor("ORDER_SHIPPED"))
                .isEqualTo("order.shipped");
    }

    @Test
    void topicForPassesThroughDotNotation() {
        assertThat(OutboxPublisher.topicFor("order.created"))
                .isEqualTo("order.created");
    }

    @Test
    void topicForHandlesMixedUnderscoreAndDot() {
        assertThat(OutboxPublisher.topicFor("payment.refund_requested"))
                .isEqualTo("payment.refund.requested");
    }

    @Test
    void topicForConvertsUppercaseSagaType() {
        assertThat(OutboxPublisher.topicFor("SAGA_STARTED"))
                .isEqualTo("saga.started");
    }

    @Test
    void publisherUsesConfiguredMaxAttempts() {
        OutboxPublisher publisher = new OutboxPublisher(null, null, 50, 8, 5000);
        assertThat(publisher.getMaxAttempts()).isEqualTo(8);
    }

    @Test
    void entityMarkPublishedSetsStatusToPublished() {
        OutboxEventJpaEntity entity = new OutboxEventJpaEntity();
        entity.setAggregateType("ORDER");
        entity.setAggregateId("order-1");
        entity.setEventType("ORDER_CREATED");
        entity.setPayload("{}");
        entity.setStatus(Status.PENDING);

        entity.markPublished();

        assertThat(entity.getStatus()).isEqualTo(Status.PUBLISHED);
    }

    @Test
    void entityRecordFailureIncrementsAttemptsAndSetsBackoff() {
        OutboxEventJpaEntity entity = new OutboxEventJpaEntity();
        entity.setAggregateType("ORDER");
        entity.setAggregateId("order-1");
        entity.setEventType("ORDER_CREATED");
        entity.setPayload("{}");
        entity.setStatus(Status.PENDING);
        entity.setAttemptCount(0);

        entity.recordFailure(8, new RuntimeException("boom"));

        assertThat(entity.getAttemptCount()).isEqualTo(1);
        assertThat(entity.getLastError()).contains("RuntimeException: boom");
        assertThat(entity.getStatus()).isEqualTo(Status.PENDING);
        assertThat(entity.getNextAttemptAt()).isNotNull();
    }

    @Test
    void entityRecordFailureMarksDeadAfterMaxAttempts() {
        OutboxEventJpaEntity entity = new OutboxEventJpaEntity();
        entity.setAggregateType("ORDER");
        entity.setAggregateId("order-1");
        entity.setEventType("ORDER_CREATED");
        entity.setPayload("{}");
        entity.setStatus(Status.PENDING);
        entity.setAttemptCount(7);

        entity.recordFailure(8, new RuntimeException("fatal"));

        assertThat(entity.getAttemptCount()).isEqualTo(8);
        assertThat(entity.getStatus()).isEqualTo(Status.DEAD);
        assertThat(entity.getLastError()).contains("RuntimeException: fatal");
    }

    @Test
    void entityRecordFailureMaxLengthTruncatesError() {
        OutboxEventJpaEntity entity = new OutboxEventJpaEntity();
        String longError = "x".repeat(3000);
        entity.setLastError(null);

        entity.recordFailure(8, new RuntimeException(longError));

        assertThat(entity.getLastError()).hasSizeLessThanOrEqualTo(2000);
    }
}