package com.vnshop.paymentservice.application;

import java.math.BigDecimal;

public record LedgerPaymentCommand(String transactionId, String orderId, BigDecimal amount) {
}
