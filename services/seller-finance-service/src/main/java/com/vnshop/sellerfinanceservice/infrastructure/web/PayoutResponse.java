package com.vnshop.sellerfinanceservice.infrastructure.web;

import com.vnshop.sellerfinanceservice.domain.Payout;

import java.math.BigDecimal;
import java.time.Instant;

public record PayoutResponse(String payoutId, String sellerId, BigDecimal amount, String status, Instant createdAt) {
    static PayoutResponse fromDomain(Payout payout) {
        return new PayoutResponse(payout.payoutId().toString(), payout.sellerId(), payout.amount(), payout.status().name(), payout.createdAt());
    }
}
