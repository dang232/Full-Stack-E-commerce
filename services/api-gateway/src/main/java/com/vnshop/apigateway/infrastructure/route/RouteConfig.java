package com.vnshop.apigateway.infrastructure.route;

import org.springframework.cloud.gateway.filter.factory.TokenRelayGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.GatewayFilterSpec;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RouteConfig {

    private static final String PRODUCT_SERVICE = "http://localhost:8082";
    private static final String USER_SERVICE = "http://localhost:8081";
    private static final String ORDER_SERVICE = "http://localhost:8091";

    @Bean
    RedisRateLimiter redisRateLimiter() {
        return new RedisRateLimiter(10, 20, 1);
    }

    @Bean
    RouteLocator gatewayRoutes(
        RouteLocatorBuilder builder,
        TokenRelayGatewayFilterFactory tokenRelay,
        RedisRateLimiter redisRateLimiter,
        KeyResolver userKeyResolver
    ) {
        return builder.routes()
            .route("products", route -> route.path("/products/**")
                .filters(filters -> rateLimited(filters, "product-service", tokenRelay, redisRateLimiter, userKeyResolver))
                .uri(PRODUCT_SERVICE))
            .route("categories", route -> route.path("/categories/**")
                .filters(filters -> resilient(filters, "product-service", tokenRelay))
                .uri(PRODUCT_SERVICE))
            .route("search", route -> route.path("/search/**")
                .filters(filters -> resilient(filters, "search-service", tokenRelay))
                .uri("http://localhost:8086"))
            .route("inventory", route -> route.path("/inventory/**")
                .filters(filters -> resilient(filters, "inventory-service", tokenRelay))
                .uri("http://localhost:8083"))
            .route("users", route -> route.path("/users/**", "/sellers/**")
                .filters(filters -> resilient(filters, "user-service", tokenRelay))
                .uri(USER_SERVICE))
            .route("cart", route -> route.path("/cart/**")
                .filters(filters -> resilient(filters, "cart-service", tokenRelay))
                .uri("http://localhost:8084"))
            .route("orders", route -> route.path("/orders/**")
                .filters(filters -> rateLimited(filters, "order-service", tokenRelay, redisRateLimiter, userKeyResolver))
                .uri(ORDER_SERVICE))
            .route("payment", route -> route.path("/payment/**")
                .filters(filters -> rateLimited(filters, "payment-service", tokenRelay, redisRateLimiter, userKeyResolver))
                .uri("http://localhost:8092"))
            .route("shipping", route -> route.path("/shipping/**")
                .filters(filters -> resilient(filters, "shipping-service", tokenRelay))
                .uri("http://localhost:8093"))
            .route("notifications", route -> route.path("/notifications/**")
                .filters(filters -> resilient(filters, "notification-service", tokenRelay))
                .uri("http://localhost:8087"))
            .route("coupons", route -> route.path("/coupons/**")
                .filters(filters -> resilient(filters, "order-service", tokenRelay))
                .uri(ORDER_SERVICE))
            .route("reviews", route -> route.path("/reviews/**")
                .filters(filters -> resilient(filters, "product-service", tokenRelay))
                .uri(PRODUCT_SERVICE))
            .route("seller-finance", route -> route.path("/seller-finance/**")
                .filters(filters -> resilient(filters, "order-service", tokenRelay))
                .uri(ORDER_SERVICE))
            .route("admin", route -> route.path("/admin/**")
                .filters(filters -> resilient(filters, "user-service", tokenRelay))
                .uri(USER_SERVICE))
            .build();
    }

    private GatewayFilterSpec resilient(
        GatewayFilterSpec filters,
        String service,
        TokenRelayGatewayFilterFactory tokenRelay
    ) {
        return filters
            .filter(tokenRelay.apply())
            .circuitBreaker(config -> config
                .setName(service)
                .setFallbackUri("forward:/fallback/" + service));
    }

    private GatewayFilterSpec rateLimited(
        GatewayFilterSpec filters,
        String service,
        TokenRelayGatewayFilterFactory tokenRelay,
        RedisRateLimiter redisRateLimiter,
        KeyResolver userKeyResolver
    ) {
        return filters
            .filter(tokenRelay.apply())
            .requestRateLimiter(config -> config
                .setRateLimiter(redisRateLimiter)
                .setKeyResolver(userKeyResolver))
            .circuitBreaker(config -> config
                .setName(service)
                .setFallbackUri("forward:/fallback/" + service));
    }
}
