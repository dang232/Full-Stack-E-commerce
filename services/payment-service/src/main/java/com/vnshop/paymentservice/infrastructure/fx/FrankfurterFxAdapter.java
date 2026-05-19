package com.vnshop.paymentservice.infrastructure.fx;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.vnshop.paymentservice.domain.port.out.FxRatePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Frankfurter ECB-sourced FX adapter. Stripe + PayPal both need amounts in
 * USD; order totals stay in VND. Cache TTL is 24h because intraday FX swings
 * are too small for sandbox to care about. Production can shorten this from
 * config.
 *
 * <p>{@link FxProperties} is registered in {@code UseCaseConfig} via
 * {@code @EnableConfigurationProperties} so this adapter is just a plain
 * component.
 */
@Component
public class FrankfurterFxAdapter implements FxRatePort {
    private static final Logger log = LoggerFactory.getLogger(FrankfurterFxAdapter.class);

    private final FxProperties properties;
    private final RestClient restClient;
    private final Cache<String, BigDecimal> cache;

    public FrankfurterFxAdapter(FxProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = Objects.requireNonNull(properties, "properties is required");
        this.restClient = Objects.requireNonNull(restClientBuilder, "restClientBuilder is required")
                .baseUrl(properties.baseUrl())
                .build();
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(properties.cacheTtl())
                .maximumSize(properties.cacheMaxEntries())
                .build();
    }

    @Override
    public BigDecimal rate(String fromCurrency, String toCurrency) {
        String from = normalize(fromCurrency);
        String to = normalize(toCurrency);
        if (from.equals(to)) {
            return BigDecimal.ONE;
        }
        String key = from + "->" + to;
        BigDecimal cached = cache.getIfPresent(key);
        if (cached != null) {
            return cached;
        }
        BigDecimal rate = fetchRate(from, to);
        cache.put(key, rate);
        return rate;
    }

    @SuppressWarnings("unchecked")
    private BigDecimal fetchRate(String from, String to) {
        try {
            Map<String, Object> response = restClient.get()
                    .uri("/latest?from={from}&to={to}", from, to)
                    .retrieve()
                    .body(Map.class);
            if (response == null) {
                return fallback(from, to, "empty response");
            }
            Object rates = response.get("rates");
            if (rates instanceof Map<?, ?> rateMap) {
                Object rateValue = rateMap.get(to);
                if (rateValue != null) {
                    return new BigDecimal(rateValue.toString());
                }
            }
            return fallback(from, to, "missing rate field");
        } catch (RuntimeException ex) {
            return fallback(from, to, ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }

    private BigDecimal fallback(String from, String to, String reason) {
        if ("USD".equals(from) && "VND".equals(to)) {
            log.warn("fx-fallback-rate from={} to={} rate={} reason={}",
                    from, to, properties.fallbackUsdToVnd(), reason);
            return properties.fallbackUsdToVnd();
        }
        if ("VND".equals(from) && "USD".equals(to)) {
            BigDecimal inverse = BigDecimal.ONE.divide(
                    properties.fallbackUsdToVnd(), 8, java.math.RoundingMode.HALF_UP);
            log.warn("fx-fallback-rate from={} to={} rate={} reason={}",
                    from, to, inverse, reason);
            return inverse;
        }
        throw new IllegalStateException(
                "no fallback rate configured for " + from + "->" + to + " (reason: " + reason + ")");
    }

    private static String normalize(String currency) {
        return Objects.requireNonNull(currency, "currency is required").toUpperCase(Locale.ROOT);
    }
}

