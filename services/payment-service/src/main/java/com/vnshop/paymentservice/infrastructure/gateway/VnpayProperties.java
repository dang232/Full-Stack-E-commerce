package com.vnshop.paymentservice.infrastructure.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payment.vnpay")
public record VnpayProperties(
        String payUrl,
        String tmnCode,
        String hashSecret,
        String returnUrl,
        String ipnUrl,
        String version,
        String command,
        String orderType,
        String locale,
        String currency,
        long expireMinutes
) {
    public VnpayProperties {
        payUrl = defaultIfBlank(payUrl, "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
        version = defaultIfBlank(version, "2.1.0");
        command = defaultIfBlank(command, "pay");
        orderType = defaultIfBlank(orderType, "other");
        locale = defaultIfBlank(locale, "vn");
        currency = defaultIfBlank(currency, "VND");
        expireMinutes = expireMinutes <= 0 ? 15 : expireMinutes;
    }

    private static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
