package com.vnshop.orderservice.infrastructure.grpc;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GrpcCircuitBreakerConfig {

    @Bean
    public CircuitBreaker inventoryCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker("inventoryService");
    }

    @Bean
    public CircuitBreaker paymentCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker("paymentService");
    }

    @Bean
    public CircuitBreaker shippingCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker("shippingService");
    }
}
