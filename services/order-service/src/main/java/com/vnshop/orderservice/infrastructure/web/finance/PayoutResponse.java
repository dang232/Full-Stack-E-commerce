package com.vnshop.orderservice.infrastructure.web.finance;

import com.vnshop.orderservice.domain.finance.Payout;
import java.math.BigDecimal;
import java.time.Instant;

public record PayoutResponse(String payoutId, String sellerId, BigDecimal amount, String status, Instant createdAt) {
    static PayoutResponse fromDomain(Payout payout) {
        return new PayoutResponse(payout.payoutId().toString(), payout.sellerId(), payout.amount(), payout.status().name(), payout.createdAt());
    }
}
