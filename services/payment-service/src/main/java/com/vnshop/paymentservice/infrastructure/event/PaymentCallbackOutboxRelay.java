package com.vnshop.paymentservice.infrastructure.event;

import com.vnshop.paymentservice.infrastructure.gateway.PaymentCallbackOutbox;
import com.vnshop.paymentservice.infrastructure.gateway.PaymentCallbackOutboxRecord;
import jakarta.annotation.PostConstruct;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Polls {@code payment_svc.payment_callback_outbox} for unpublished rows and
 * fans them out to Kafka topic {@code payment.completed}, regardless of
 * provider. Mirrors the order-service {@code OutboxPublisher} shape: scheduled
 * batch poll, send-then-mark, fail-quietly so a Kafka outage just means the
 * row stays {@code published_at IS NULL} and the next tick retries.
 *
 * <p>Every promoted payment (VNPay, MoMo, Stripe, PayPal — every adapter that
 * goes through {@code PaymentPromotionService}) writes an outbox row inside the
 * promote transaction. Without this relay those rows accumulate forever and
 * order-service never learns the payment completed.
 */
@Service
public class PaymentCallbackOutboxRelay {
    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentCallbackOutboxRelay.class);
    static final String TOPIC = "payment.completed";

    private final PaymentCallbackOutbox outbox;
    private final ObjectProvider<KafkaTemplate<String, Object>> kafkaTemplateProvider;
    private final int batchSize;

    public PaymentCallbackOutboxRelay(
            PaymentCallbackOutbox outbox,
            ObjectProvider<KafkaTemplate<String, Object>> kafkaTemplateProvider,
            @Value("${payment.outbox.batch-size:50}") int batchSize) {
        this.outbox = outbox;
        this.kafkaTemplateProvider = kafkaTemplateProvider;
        this.batchSize = batchSize;
    }

    @PostConstruct
    void warnIfKafkaTemplateMissing() {
        if (kafkaTemplateProvider.getIfAvailable() == null) {
            LOGGER.warn("PaymentCallbackOutboxRelay started without a KafkaTemplate bean — payment.completed events will accumulate as unpublished until Kafka is configured.");
        }
    }

    @Scheduled(fixedDelayString = "${payment.outbox.poll-interval-ms:1000}")
    public void publishPending() {
        KafkaTemplate<String, Object> kafkaTemplate = kafkaTemplateProvider.getIfAvailable();
        if (kafkaTemplate == null) {
            return;
        }
        List<PaymentCallbackOutboxRecord> pending = outbox.findUnpublished(batchSize);
        for (PaymentCallbackOutboxRecord record : pending) {
            publish(kafkaTemplate, record);
        }
    }

    private void publish(KafkaTemplate<String, Object> kafkaTemplate, PaymentCallbackOutboxRecord record) {
        try {
            PaymentCompletedEvent event = new PaymentCompletedEvent(
                    record.provider(),
                    record.paymentId(),
                    record.orderId(),
                    record.transactionRef(),
                    record.status(),
                    record.amount(),
                    record.currency(),
                    record.callbackId(),
                    record.callbackEventId(),
                    record.externalAmount(),
                    record.externalCurrency(),
                    record.fxRate(),
                    record.fxRateAt());
            kafkaTemplate.send(TOPIC, record.orderId(), event);
            outbox.markPublished(record.id());
            LOGGER.debug("payment-callback-outbox published id={} provider={} orderId={}",
                    record.id(), record.provider(), record.orderId());
        } catch (RuntimeException e) {
            LOGGER.warn("payment-callback-outbox publish failed id={} provider={} orderId={}: {}",
                    record.id(), record.provider(), record.orderId(), e.getMessage());
        }
    }
}
