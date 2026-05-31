package com.vnshop.sellerfinanceservice.infrastructure.dto;

import java.math.BigDecimal;

public record PayoutRequest(String sellerId, BigDecimal amount) {
}
