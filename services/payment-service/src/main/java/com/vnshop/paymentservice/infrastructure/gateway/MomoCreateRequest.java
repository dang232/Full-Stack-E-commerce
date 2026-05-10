package com.vnshop.paymentservice.infrastructure.gateway;

public record MomoCreateRequest(
        String partnerCode,
        String accessKey,
        String requestId,
        long amount,
        String orderId,
        String orderInfo,
        String redirectUrl,
        String ipnUrl,
        String extraData,
        String requestType,
        String lang,
        String signature
) {
}
