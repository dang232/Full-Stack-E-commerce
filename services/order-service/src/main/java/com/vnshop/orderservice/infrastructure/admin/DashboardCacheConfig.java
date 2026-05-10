package com.vnshop.orderservice.infrastructure.admin;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.time.Duration;
import java.util.Map;

@Configuration
@EnableCaching
public class DashboardCacheConfig {
    private static final Duration DASHBOARD_TTL = Duration.ofMinutes(5);

    @Bean
    CacheManager cacheManager(RedisConnectionFactory redisConnectionFactory) {
        RedisCacheConfiguration configuration = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(DASHBOARD_TTL)
                .disableCachingNullValues();

        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(configuration)
                .withInitialCacheConfigurations(Map.of(
                        "dashboardSummary", configuration,
                        "dashboardRevenue", configuration,
                        "dashboardTopProducts", configuration,
                        "dashboardTopSellers", configuration
                ))
                .build();
    }
}
