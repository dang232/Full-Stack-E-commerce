package com.vnshop.paymentservice.infrastructure.gateway;

public record MomoQueryDrRequest(
        String partnerCode,
        String accessKey,
        String requestId,
        String orderId,
        String lang,
        String signature
) {
}
