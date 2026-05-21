package com.vnshop.paymentservice.infrastructure.order;

import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/**
 * Declarative HTTP client for order-service. Spring generates the proxy at
 * startup via {@link OrderHttpClientConfig}; callers never instantiate this
 * directly.
 *
 * <p>The {@code Authorization} header is optional so the proxy can be called
 * from unauthenticated contexts (e.g. background jobs) without throwing.
 * {@link OrderCatalogAdapter} is responsible for extracting the caller's JWT
 * from the security context and forwarding it here.
 */
@HttpExchange
public interface OrderHttpClient {

    @GetExchange("/orders/{orderId}")
    String getOrder(
            @PathVariable String orderId,
            @RequestHeader(value = "Authorization", required = false) @Nullable String authorization);
}
