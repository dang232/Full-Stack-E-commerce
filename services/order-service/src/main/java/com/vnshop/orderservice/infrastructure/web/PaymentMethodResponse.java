package com.vnshop.orderservice.infrastructure.web;

/**
 * Catalog entry for a payment method exposed by /checkout/payment-methods.
 * The shape mirrors the FE {@code paymentMethodSchema} (code, name, description?,
 * enabled) so the FE can drop its FALLBACK_PAYMENT array.
 *
 * <p>Configurability (per-deploy enable/disable, gradual rollout) is intentionally
 * deferred. Today the list is static; per-provider runtime status will move into
 * {@code application.yml} via {@code @ConfigurationProperties} in a follow-up.
 */
public record PaymentMethodResponse(
        String code,
        String name,
        String description,
        boolean enabled) {
}
