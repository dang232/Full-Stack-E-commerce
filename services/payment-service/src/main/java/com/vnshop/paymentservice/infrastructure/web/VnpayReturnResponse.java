package com.vnshop.paymentservice.infrastructure.web;

public record VnpayReturnResponse(boolean validSignature, String gatewayStatus, String paymentId, String transactionNo) {
}
