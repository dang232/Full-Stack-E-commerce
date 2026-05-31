package com.vnshop.paymentservice.infrastructure.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.springframework.kafka.core.KafkaTemplate;

class KafkaProducerHealthIndicatorTest {

    @Test
    @SuppressWarnings("unchecked")
    void reportsUpWhenBrokerReachable() {
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.execute(any())).thenReturn(true);

        KafkaProducerHealthIndicator indicator = new KafkaProducerHealthIndicator(kafkaTemplate);
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("component", "kafka-producer");
    }

    @Test
    @SuppressWarnings("unchecked")
    void reportsDownWhenBrokerUnreachable() {
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.execute(any())).thenThrow(new RuntimeException("Connection refused"));

        KafkaProducerHealthIndicator indicator = new KafkaProducerHealthIndicator(kafkaTemplate);
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("component", "kafka-producer");
    }
}
