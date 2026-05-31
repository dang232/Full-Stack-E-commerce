package com.vnshop.paymentservice.application;

/**
 * Inbound command for {@link ProcessPaymentUseCase}. Note the absence of an
 * {@code amount} field — the use case looks the authoritative amount up
 * server-side via {@link com.vnshop.paymentservice.domain.port.out.OrderCatalogPort}
 * to close the price-tampering finding documented in
 * {@code docs/SESSION-HANDOVER-2026-05-20-pt12.md}.
 *
 * <p>{@code buyerId} is sourced from the JWT principal at the controller
 * boundary, never from the request body. The use case re-validates it
 * matches the order's owner.
 */
public record ProcessPaymentCommand(
        String orderId,
        String buyerId,
        PaymentMethodInput method,
        String idempotencyKey
) {
    public ProcessPaymentCommand(String orderId, String buyerId, PaymentMethodInput method) {
        this(orderId, buyerId, method, null);
    }
}
