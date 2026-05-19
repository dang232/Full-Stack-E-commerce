package com.vnshop.paymentservice.infrastructure.stripe;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Stripe sandbox/live credentials.
 *
 * <p>Resolution order at startup:
 * <ol>
 *   <li>If {@code payment.stripe.enabled=false}, this bean is never created.</li>
 *   <li>If enabled, {@code secretKey} and {@code webhookSecret} must be non-blank
 *       — both are validated in {@link StripeGateway} so a misconfigured deploy
 *       fails fast instead of silently 500-ing on the first checkout.</li>
 *   <li>{@code publishableKey} is consumed by the FE bundle (via VITE_…) and
 *       merely echoed back from a {@code /payment/stripe/config} read endpoint.</li>
 * </ol>
 */
@ConfigurationProperties(prefix = "payment.stripe")
public record StripeProperties(
        boolean enabled,
        String secretKey,
        String publishableKey,
        String webhookSecret) {
}
