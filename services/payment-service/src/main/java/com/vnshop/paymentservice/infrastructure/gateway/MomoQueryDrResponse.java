package com.vnshop.paymentservice.infrastructure.gateway;

public record MomoQueryDrResponse(
        String partnerCode,
        String requestId,
        String orderId,
        long amount,
        long transId,
        int resultCode,
        String message,
        long responseTime
) {
}
