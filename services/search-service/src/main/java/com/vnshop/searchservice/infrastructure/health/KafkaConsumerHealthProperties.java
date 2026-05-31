package com.vnshop.searchservice.infrastructure.health;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("kafka.health.consumer")
public record KafkaConsumerHealthProperties(
    boolean enabled,
    long lagThreshold,
    String groupId
) {
    public KafkaConsumerHealthProperties {
        if (lagThreshold <= 0) lagThreshold = 1000;
    }
}
