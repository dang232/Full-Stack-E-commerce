package com.vnshop.paymentservice.infrastructure.fx;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * FX adapter configuration. Defaults match the spec:
 * Frankfurter ECB-sourced rates, 24h cache, 25500 VND/USD fallback.
 *
 * <p>Bound from {@code payment.fx.*} (see application.yml). The fallback rate
 * is applied any time the upstream lookup fails — and the adapter logs a WARN
 * on every fallback hit so an outage doesn't ship as a stale-rate slow leak.
 */
@ConfigurationProperties(prefix = "payment.fx")
public record FxProperties(
        String baseUrl,
        Duration cacheTtl,
        int cacheMaxEntries,
        BigDecimal fallbackUsdToVnd) {
    public FxProperties {
        if (baseUrl == null || baseUrl.isBlank()) baseUrl = "https://api.frankfurter.app";
        if (cacheTtl == null) cacheTtl = Duration.ofHours(24);
        if (cacheMaxEntries <= 0) cacheMaxEntries = 100;
        if (fallbackUsdToVnd == null) fallbackUsdToVnd = new BigDecimal("25500");
    }
}
