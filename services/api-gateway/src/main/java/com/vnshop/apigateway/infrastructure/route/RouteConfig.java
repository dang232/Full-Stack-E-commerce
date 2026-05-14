package com.vnshop.apigateway.infrastructure.route;

import org.springframework.cloud.gateway.filter.factory.TokenRelayGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.GatewayFilterSpec;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RouteConfig {

    private final String productServiceUri;
    private final String userServiceUri;
    private final String searchServiceUri;
    private final String inventoryServiceUri;
    private final String cartServiceUri;
    private final String orderServiceUri;
    private final String paymentServiceUri;
    private final String shippingServiceUri;
    private final String notificationServiceUri;
    private final String couponServiceUri;
    private final String sellerFinanceServiceUri;

    public RouteConfig(
        @Value("${vnshop.routes.product-service:http://product-service:8082}") String productServiceUri,
        @Value("${vnshop.routes.user-service:http://user-service:8081}") String userServiceUri,
        @Value("${vnshop.routes.search-service:http://search-service:8086}") String searchServiceUri,
        @Value("${vnshop.routes.inventory-service:http://inventory-service:8083}") String inventoryServiceUri,
        @Value("${vnshop.routes.cart-service:http://cart-service:8084}") String cartServiceUri,
        @Value("${vnshop.routes.order-service:http://order-service:8091}") String orderServiceUri,
        @Value("${vnshop.routes.payment-service:http://payment-service:8092}") String paymentServiceUri,
        @Value("${vnshop.routes.shipping-service:http://shipping-service:8093}") String shippingServiceUri,
        @Value("${vnshop.routes.notification-service:http://notification-service:8087}") String notificationServiceUri,
        @Value("${vnshop.routes.coupon-service:http://coupon-service:8088}") String couponServiceUri,
        @Value("${vnshop.routes.seller-finance-service:http://seller-finance-service:8090}") String sellerFinanceServiceUri
    ) {
        this.productServiceUri = productServiceUri;
        this.userServiceUri = userServiceUri;
        this.searchServiceUri = searchServiceUri;
        this.inventoryServiceUri = inventoryServiceUri;
        this.cartServiceUri = cartServiceUri;
        this.orderServiceUri = orderServiceUri;
        this.paymentServiceUri = paymentServiceUri;
        this.shippingServiceUri = shippingServiceUri;
        this.notificationServiceUri = notificationServiceUri;
        this.couponServiceUri = couponServiceUri;
        this.sellerFinanceServiceUri = sellerFinanceServiceUri;
    }

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
                .uri(productServiceUri))
            .route("categories", route -> route.path("/categories/**")
                .filters(filters -> resilient(filters, "product-service", tokenRelay))
                .uri(productServiceUri))
            .route("search", route -> route.path("/search/**")
                .filters(filters -> resilient(filters, "search-service", tokenRelay))
                .uri(searchServiceUri))
            .route("inventory", route -> route.path("/inventory/**")
                .filters(filters -> resilient(filters, "inventory-service", tokenRelay))
                .uri(inventoryServiceUri))
            .route("users", route -> route.path("/users/**", "/sellers/**")
                .filters(filters -> resilient(filters, "user-service", tokenRelay))
                .uri(userServiceUri))
            .route("cart", route -> route.path("/cart/**")
                .filters(filters -> resilient(filters, "cart-service", tokenRelay))
                .uri(cartServiceUri))
            .route("orders", route -> route.path("/orders/**")
                .filters(filters -> rateLimited(filters, "order-service", tokenRelay, redisRateLimiter, userKeyResolver))
                .uri(orderServiceUri))
            .route("payment", route -> route.path("/payment/**")
                .filters(filters -> rateLimited(filters, "payment-service", tokenRelay, redisRateLimiter, userKeyResolver))
                .uri(paymentServiceUri))
            .route("shipping", route -> route.path("/shipping/**")
                .filters(filters -> resilient(filters, "shipping-service", tokenRelay))
                .uri(shippingServiceUri))
            .route("notifications", route -> route.path("/notifications/**")
                .filters(filters -> resilient(filters, "notification-service", tokenRelay))
                .uri(notificationServiceUri))
            .route("coupons", route -> route.path("/coupons/**")
                .filters(filters -> resilient(filters, "order-service", tokenRelay))
                .uri(couponServiceUri))
            .route("reviews", route -> route.path("/reviews/**")
                .filters(filters -> resilient(filters, "product-service", tokenRelay))
                .uri(productServiceUri))
            .route("seller-finance", route -> route.path("/seller-finance/**")
                .filters(filters -> resilient(filters, "order-service", tokenRelay))
                .uri(sellerFinanceServiceUri))
            .route("admin", route -> route.path("/admin/**")
                .filters(filters -> resilient(filters, "user-service", tokenRelay))
                .uri(userServiceUri))
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
