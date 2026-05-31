package com.vnshop.orderservice.application.shipping;

import java.util.List;

/**
 * Outbound port for shipping-service /shipping/rate-quotes. The order-service
 * checkout flow asks for buyer-facing shipping options based on a destination
 * address; the adapter side wraps that in an HTTP call.
 *
 * <p>Implementations must degrade gracefully — if the carrier or shipping
 * service is unreachable, return an empty list and let the caller surface a
 * static fallback. We never want to 500 the buyer at checkout.
 */
public interface ShippingQuotePort {
    List<ShippingOption> quote(ShippingQuoteRequest request);
}
