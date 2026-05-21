package com.vnshop.orderservice.infrastructure.cart;

import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/**
 * Declarative HTTP client for cart-service. Spring generates the proxy at
 * startup via {@link CartHttpClientConfig}; callers never instantiate this
 * directly.
 *
 * <p>The cart endpoint identifies the cart by the {@code x-user-id} request
 * header rather than a path segment, so we forward it as a
 * {@link RequestHeader}.
 */
@HttpExchange
public interface CartHttpClient {

    /**
     * Fetch the active cart for {@code userId}. Returns the raw JSON body so
     * that {@link CartServiceAdapter} can apply its existing null-safe mapping
     * and domain-exception translation without duplicating that logic here.
     *
     * @param userId the buyer's user-id, forwarded as {@code x-user-id}
     * @return raw JSON string from cart-service, or {@code null} if the body
     *         is empty
     */
    /**
     * Returns the raw JSON body so that {@link CartServiceAdapter} can apply
     * its existing null-safe mapping and domain-exception translation without
     * duplicating that logic here.
     */
    @GetExchange("/cart")
    String getCart(@RequestHeader("x-user-id") String userId);
}
