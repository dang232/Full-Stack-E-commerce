package com.vnshop.paymentservice.infrastructure.paypal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * PayPal sandbox/live credentials. {@link #mode} maps to a base URL —
 * {@code sandbox} → {@code https://api-m.sandbox.paypal.com},
 * {@code live} → {@code https://api-m.paypal.com}.
 *
 * <p>{@code clientId} is consumed by the FE bundle (echoed back from a
 * read endpoint); {@code clientSecret} stays server-side and is used to
 * mint the OAuth bearer for the {@code /v2/checkout/orders} calls.
 */
@ConfigurationProperties(prefix = "payment.paypal")
public record PayPalProperties(
        boolean enabled,
        String clientId,
        String clientSecret,
        String mode) {

    public String baseUrl() {
        return "live".equalsIgnoreCase(mode) ? "https://api-m.paypal.com" : "https://api-m.sandbox.paypal.com";
    }
}
