package com.vnshop.paymentservice.infrastructure.health;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Surfaces Kafka broker connectivity in the /actuator/health endpoint.
 * Calls {@code partitionsFor} on a metadata-only topic — lightweight,
 * no message produced. Returns DOWN if the broker is unreachable.
 */
@Component
@ConditionalOnBean(KafkaTemplate.class)
public class KafkaProducerHealthIndicator implements HealthIndicator {

    private final KafkaTemplate<?, ?> kafkaTemplate;

    public KafkaProducerHealthIndicator(KafkaTemplate<?, ?> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public Health health() {
        try {
            kafkaTemplate.execute(producer -> {
                producer.partitionsFor("__health_check");
                return true;
            });
            return Health.up()
                    .withDetail("component", "kafka-producer")
                    .build();
        } catch (Exception ex) {
            return Health.down(ex)
                    .withDetail("component", "kafka-producer")
                    .build();
        }
    }
}
