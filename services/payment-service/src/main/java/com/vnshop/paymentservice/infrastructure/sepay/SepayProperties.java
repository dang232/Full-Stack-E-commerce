package com.vnshop.paymentservice.infrastructure.sepay;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SePay polling configuration. {@code apiKey} is required when
 * {@link #enabled} is true — startup-validated in {@link SepayPoller} so a
 * misconfigured deploy fails fast instead of thrashing the API with 401s.
 *
 * <p>{@code accountId} is the bank account id registered with SePay (the
 * dashboard exposes it as a numeric id under each linked account).
 */
@ConfigurationProperties(prefix = "payment.sepay")
public record SepayProperties(
        boolean enabled,
        String apiKey,
        String accountId,
        String baseUrl,
        long pollIntervalSeconds) {
    public SepayProperties {
        if (baseUrl == null || baseUrl.isBlank()) baseUrl = "https://my.sepay.vn/userapi";
        if (pollIntervalSeconds <= 0) pollIntervalSeconds = 30;
    }
}
