package com.vnshop.sellerfinanceservice.infrastructure.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionResponse(String transactionId, String sellerId, BigDecimal amount, String type, Instant createdAt) {
}
