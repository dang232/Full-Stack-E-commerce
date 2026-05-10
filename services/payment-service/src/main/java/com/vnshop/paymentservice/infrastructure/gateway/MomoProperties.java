package com.vnshop.paymentservice.infrastructure.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payment.momo")
public record MomoProperties(
        String createEndpoint,
        String queryDrEndpoint,
        String partnerCode,
        String accessKey,
        String secretKey,
        String redirectUrl,
        String ipnUrl,
        String requestType,
        String lang
) {
    public MomoProperties {
        createEndpoint = defaultIfBlank(createEndpoint, "https://test-payment.momo.vn/v2/gateway/api/create");
        queryDrEndpoint = defaultIfBlank(queryDrEndpoint, "https://test-payment.momo.vn/v2/gateway/api/query");
        requestType = defaultIfBlank(requestType, "captureWallet");
        lang = defaultIfBlank(lang, "vi");
    }

    private static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
