package com.vnshop.searchservice.infrastructure.health;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsResult;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KafkaConsumerHealthIndicatorTest {

    @Mock AdminClient adminClient;
    @Mock ListConsumerGroupOffsetsResult listOffsetsResult;
    @Mock ListOffsetsResult endOffsetsResult;

    KafkaConsumerHealthProperties props;
    KafkaConsumerHealthIndicator indicator;

    @BeforeEach
    void setUp() {
        props = new KafkaConsumerHealthProperties(true, 1000, "search-service");
        indicator = new KafkaConsumerHealthIndicator(adminClient, props);
    }

    @Test
    void healthyWhenLagBelowThreshold() throws Exception {
        TopicPartition tp = new TopicPartition("product-events", 0);
        Map<TopicPartition, OffsetAndMetadata> offsets = Map.of(tp, new OffsetAndMetadata(90));

        when(adminClient.listConsumerGroupOffsets(anyString())).thenReturn(listOffsetsResult);
        when(listOffsetsResult.partitionsToOffsetAndMetadata()).thenReturn(KafkaFuture.completedFuture(offsets));

        when(adminClient.listOffsets(any())).thenReturn(endOffsetsResult);
        var endOffset = new ListOffsetsResult.ListOffsetsResultInfo(100, 0, null);
        when(endOffsetsResult.all()).thenReturn(KafkaFuture.completedFuture(Map.of(tp, endOffset)));

        Health health = indicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("totalLag", 10L);
    }

    @Test
    void unhealthyWhenLagAboveThreshold() throws Exception {
        TopicPartition tp = new TopicPartition("product-events", 0);
        Map<TopicPartition, OffsetAndMetadata> offsets = Map.of(tp, new OffsetAndMetadata(0));

        when(adminClient.listConsumerGroupOffsets(anyString())).thenReturn(listOffsetsResult);
        when(listOffsetsResult.partitionsToOffsetAndMetadata()).thenReturn(KafkaFuture.completedFuture(offsets));

        when(adminClient.listOffsets(any())).thenReturn(endOffsetsResult);
        var endOffset = new ListOffsetsResult.ListOffsetsResultInfo(5000, 0, null);
        when(endOffsetsResult.all()).thenReturn(KafkaFuture.completedFuture(Map.of(tp, endOffset)));

        Health health = indicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("totalLag", 5000L);
    }

    @Test
    void unhealthyWhenBrokerUnreachable() {
        when(adminClient.listConsumerGroupOffsets(anyString())).thenThrow(new RuntimeException("Connection refused"));

        Health health = indicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsKey("error");
    }
}
