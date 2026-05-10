package com.vnshop.paymentservice.infrastructure.gateway;

public record MomoCreateResponse(
        String partnerCode,
        String requestId,
        String orderId,
        long amount,
        long responseTime,
        String message,
        int resultCode,
        String payUrl,
        String deeplink,
        String qrCodeUrl
) {
}
