package com.vnshop.paymentservice.infrastructure.gateway;

public record MomoIpnRequest(
        String partnerCode,
        String accessKey,
        String requestId,
        long amount,
        String orderId,
        String orderInfo,
        String orderType,
        long transId,
        int resultCode,
        String message,
        String payType,
        long responseTime,
        String extraData,
        String signature
) {
}
