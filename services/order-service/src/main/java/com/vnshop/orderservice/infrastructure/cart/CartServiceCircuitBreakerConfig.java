package com.vnshop.orderservice.infrastructure.cart;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Circuit breaker around the synchronous cart-service edge. Opens after a sustained
 * failure rate so checkout fails fast (and lets clients see "cart unavailable") instead
 * of timing out request-by-request and tying up gateway threads.
 *
 * <p>The adapter handles 404 in-band by returning an empty {@code CartSnapshot}, so the
 * CB only counts actual failures: connect/read timeouts, non-404 HTTP errors, and JSON
 * parse failures — all of which surface as {@code CartUnavailableException}.
 *
 * <p>Tunables exposed as {@code vnshop.cart-service.circuit-breaker.*}; defaults follow
 * the gateway's downstream-CB defaults so behaviour is consistent across the platform.
 */
@Configuration
public class CartServiceCircuitBreakerConfig {

    public static final String CART_SERVICE_CB = "cart-service";

    @Bean
    public CircuitBreakerRegistry cartCircuitBreakerRegistry(
            @Value("${vnshop.cart-service.circuit-breaker.failure-rate-threshold:50}")
            float failureRateThreshold,
            @Value("${vnshop.cart-service.circuit-breaker.slow-call-rate-threshold:80}")
            float slowCallRateThreshold,
            @Value("${vnshop.cart-service.circuit-breaker.slow-call-duration-threshold-ms:1500}")
            long slowCallDurationThresholdMs,
            @Value("${vnshop.cart-service.circuit-breaker.sliding-window-size:20}")
            int slidingWindowSize,
            @Value("${vnshop.cart-service.circuit-breaker.minimum-number-of-calls:10}")
            int minimumNumberOfCalls,
            @Value("${vnshop.cart-service.circuit-breaker.wait-duration-in-open-state-ms:10000}")
            long waitDurationInOpenStateMs,
            @Value("${vnshop.cart-service.circuit-breaker.permitted-calls-in-half-open:3}")
            int permittedCallsInHalfOpen) {

        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(failureRateThreshold)
                .slowCallRateThreshold(slowCallRateThreshold)
                .slowCallDurationThreshold(Duration.ofMillis(slowCallDurationThresholdMs))
                .slidingWindowSize(slidingWindowSize)
                .minimumNumberOfCalls(minimumNumberOfCalls)
                .waitDurationInOpenState(Duration.ofMillis(waitDurationInOpenStateMs))
                .permittedNumberOfCallsInHalfOpenState(permittedCallsInHalfOpen)
                .build();

        return CircuitBreakerRegistry.of(config);
    }

    @Bean(name = "cartServiceCircuitBreaker")
    public CircuitBreaker cartServiceCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker(CART_SERVICE_CB);
    }
}

