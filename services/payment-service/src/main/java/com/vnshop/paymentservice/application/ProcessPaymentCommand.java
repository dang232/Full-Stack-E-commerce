package com.vnshop.paymentservice.application;

import com.vnshop.paymentservice.domain.PaymentMethod;

import java.math.BigDecimal;

public record ProcessPaymentCommand(String orderId, String buyerId, BigDecimal amount, PaymentMethod method) {
}
