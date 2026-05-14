package com.vnshop.sellerfinanceservice.infrastructure.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record PayoutResponse(String payoutId, String sellerId, BigDecimal amount, String status, Instant createdAt) {
}
