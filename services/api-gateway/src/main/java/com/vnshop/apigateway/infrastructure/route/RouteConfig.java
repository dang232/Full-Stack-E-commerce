package com.vnshop.apigateway.infrastructure.route;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.GatewayFilterSpec;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The gateway is a pure JWT-validating reverse proxy: the SPA acquires tokens directly
 * from Keycloak via PKCE and sends them as Bearer headers, which the resource-server
 * filter validates. The previous TokenRelay setup (which required an OAuth2 client
 * registration that did OIDC discovery at boot) has been dropped because it
 * (a) wasn't actually used by the SPA flow and (b) failed when KC_HOSTNAME differs
 * between in-network JWKS fetches and external token issuance.
 *
 * <p>The Authorization header is forwarded to downstream services automatically by
 * Spring Cloud Gateway, so dropping TokenRelay does not lose anything.
 */
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
        RedisRateLimiter redisRateLimiter,
        KeyResolver userKeyResolver
    ) {
        return builder.routes()
            .route("products", route -> route.path("/products/**")
                .filters(filters -> rateLimited(filters, "product-service", redisRateLimiter, userKeyResolver))
                .uri(productServiceUri))
            .route("categories", route -> route.path("/categories/**")
                .filters(filters -> resilient(filters, "product-service"))
                .uri(productServiceUri))
            .route("search", route -> route.path("/search/**")
                .filters(filters -> resilient(filters, "search-service"))
                .uri(searchServiceUri))
            .route("inventory", route -> route.path("/inventory/**")
                .filters(filters -> resilient(filters, "inventory-service"))
                .uri(inventoryServiceUri))
            .route("flash-sale", route -> route.path("/flash-sale/**")
                .filters(filters -> resilient(filters, "inventory-service"))
                .uri(inventoryServiceUri))
            .route("questions", route -> route.path("/questions/**")
                .filters(filters -> resilient(filters, "product-service"))
                .uri(productServiceUri))
            // Seller-owned product CRUD + image upload routes live on product-service.
            // Must precede the broader /sellers/** route below (which targets user-service
            // for seller profile/onboarding endpoints).
            .route("seller-products", route -> route.path("/sellers/me/products/**")
                .filters(filters -> resilient(filters, "product-service"))
                .uri(productServiceUri))
            .route("users", route -> route.path("/users/**", "/sellers/**")
                .filters(filters -> resilient(filters, "user-service"))
                .uri(userServiceUri))
            .route("cart", route -> route.path("/cart/**")
                .filters(filters -> resilient(filters, "cart-service"))
                .uri(cartServiceUri))
            // Seller fulfilment endpoints (accept/reject/ship sub-orders) live on
            // order-service. Path is /seller/orders/** (singular), distinct from the
            // /sellers/** profile/onboarding routes above.
            .route("seller-orders", route -> route.path("/seller/orders/**")
                .filters(filters -> resilient(filters, "order-service"))
                .uri(orderServiceUri))
            // Coupon-service owns the /checkout/{validate,apply}-coupon aliases
            // (legacy paths kept alongside /coupons/validate). These specific
            // routes must precede the broader /checkout/** -> order-service
            // route below so the alias keeps reaching coupon-service.
            .route("checkout-coupons", route -> route.path("/checkout/validate-coupon", "/checkout/apply-coupon")
                .filters(filters -> resilient(filters, "coupon-service"))
                .uri(couponServiceUri))
            .route("checkout", route -> route.path("/checkout/**")
                .filters(filters -> resilient(filters, "order-service"))
                .uri(orderServiceUri))
            .route("returns", route -> route.path("/returns/**")
                .filters(filters -> resilient(filters, "order-service"))
                .uri(orderServiceUri))
            .route("invoices", route -> route.path("/invoices/**")
                .filters(filters -> resilient(filters, "order-service"))
                .uri(orderServiceUri))
            .route("orders", route -> route.path("/orders/**")
                .filters(filters -> rateLimited(filters, "order-service", redisRateLimiter, userKeyResolver))
                .uri(orderServiceUri))
            .route("payment", route -> route.path("/payment/**")
                .filters(filters -> rateLimited(filters, "payment-service", redisRateLimiter, userKeyResolver))
                .uri(paymentServiceUri))
            .route("shipping", route -> route.path("/shipping/**")
                .filters(filters -> resilient(filters, "shipping-service"))
                .uri(shippingServiceUri))
            .route("notifications", route -> route.path("/notifications/**")
                .filters(filters -> resilient(filters, "notification-service"))
                .uri(notificationServiceUri))
            .route("coupons", route -> route.path("/coupons/**")
                .filters(filters -> resilient(filters, "coupon-service"))
                .uri(couponServiceUri))
            .route("reviews", route -> route.path("/reviews/**")
                .filters(filters -> resilient(filters, "product-service"))
                .uri(productServiceUri))
            .route("seller-finance", route -> route.path("/seller-finance/**")
                .filters(filters -> resilient(filters, "seller-finance-service"))
                .uri(sellerFinanceServiceUri))
            // Admin sub-routes — more specific patterns must come before the
            // catch-all /admin/** route below. Each maps to the service that owns
            // the corresponding domain endpoints.
            .route("admin-dashboard", route -> route.path("/admin/dashboard/**")
                .filters(filters -> resilient(filters, "order-service"))
                .uri(orderServiceUri))
            .route("admin-disputes", route -> route.path("/admin/disputes/**")
                .filters(filters -> resilient(filters, "order-service"))
                .uri(orderServiceUri))
            .route("admin-coupons", route -> route.path("/admin/coupons/**")
                .filters(filters -> resilient(filters, "coupon-service"))
                .uri(couponServiceUri))
            .route("admin-finance", route -> route.path("/admin/finance/**")
                .filters(filters -> resilient(filters, "order-service"))
                .uri(sellerFinanceServiceUri))
            .route("admin-reviews", route -> route.path("/admin/reviews/**")
                .filters(filters -> resilient(filters, "product-service"))
                .uri(productServiceUri))
            .route("admin", route -> route.path("/admin/**")
                .filters(filters -> resilient(filters, "user-service"))
                .uri(userServiceUri))
            .build();
    }

    private GatewayFilterSpec resilient(GatewayFilterSpec filters, String service) {
        return filters.circuitBreaker(config -> config
            .setName(service)
            .setFallbackUri("forward:/fallback/" + service));
    }

    private GatewayFilterSpec rateLimited(
        GatewayFilterSpec filters,
        String service,
        RedisRateLimiter redisRateLimiter,
        KeyResolver userKeyResolver
    ) {
        return filters
            .requestRateLimiter(config -> config
                .setRateLimiter(redisRateLimiter)
                .setKeyResolver(userKeyResolver))
            .circuitBreaker(config -> config
                .setName(service)
                .setFallbackUri("forward:/fallback/" + service));
    }
}
