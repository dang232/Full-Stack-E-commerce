package com.vnshop.recommendationsservice.infrastructure.health;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(name = "kafka.health.consumer.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaConsumerHealthIndicator implements HealthIndicator {
    private final AdminClient adminClient;
    private final KafkaConsumerHealthProperties props;

    public KafkaConsumerHealthIndicator(AdminClient adminClient, KafkaConsumerHealthProperties props) {
        this.adminClient = adminClient;
        this.props = props;
    }

    @Override
    public Health health() {
        try {
            var groupOffsets = adminClient
                .listConsumerGroupOffsets(props.groupId())
                .partitionsToOffsetAndMetadata().get(5, TimeUnit.SECONDS);

            if (groupOffsets.isEmpty()) {
                return Health.up()
                    .withDetail("groupId", props.groupId())
                    .withDetail("warning", "no committed offsets yet")
                    .build();
            }

            var topicPartitions = groupOffsets.keySet();
            Map<TopicPartition, OffsetSpec> latestRequest = topicPartitions.stream()
                .collect(Collectors.toMap(tp -> tp, tp -> OffsetSpec.latest()));

            var latestOffsets = adminClient
                .listOffsets(latestRequest)
                .all().get(5, TimeUnit.SECONDS);

            long totalLag = topicPartitions.stream()
                .mapToLong(tp -> {
                    long end = latestOffsets.get(tp).offset();
                    long current = groupOffsets.get(tp).offset();
                    return Math.max(0, end - current);
                })
                .sum();

            var details = Map.of(
                "totalLag", totalLag,
                "groupId", props.groupId(),
                "threshold", props.lagThreshold()
            );

            return totalLag <= props.lagThreshold()
                ? Health.up().withDetails(details).build()
                : Health.down().withDetails(details).build();
        } catch (Exception e) {
            return Health.down()
                .withDetail("error", e.getMessage())
                .withDetail("groupId", props.groupId())
                .build();
        }
    }
}
