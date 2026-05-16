package com.vnshop.orderservice.infrastructure.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;
import java.util.Map;

@Configuration
@EnableCaching
public class DashboardCacheConfig {
    private static final Duration DASHBOARD_TTL = Duration.ofMinutes(5);

    /**
     * Spring Cache's default Redis serializer is JDK serialization, which requires
     * every cached value to implement {@link java.io.Serializable}. The dashboard
     * domain records (DashboardSummary, RevenueTimeSeries, etc.) deliberately
     * don't, so we configure Jackson JSON serialization instead. {@code findAndRegisterModules}
     * picks up jsr310 if it's on the classpath so {@code LocalDate}/{@code Instant}
     * round-trip cleanly.
     */
    @Bean
    CacheManager cacheManager(RedisConnectionFactory redisConnectionFactory) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();

        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(mapper);

        RedisCacheConfiguration configuration = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(DASHBOARD_TTL)
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer));

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
