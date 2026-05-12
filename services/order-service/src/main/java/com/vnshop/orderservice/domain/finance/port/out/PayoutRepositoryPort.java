package com.vnshop.orderservice.domain.finance.port.out;

import com.vnshop.orderservice.domain.finance.Payout;
import com.vnshop.orderservice.domain.finance.PayoutStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PayoutRepositoryPort {
    Payout save(Payout payout);

    Optional<Payout> findById(UUID payoutId);

    List<Payout> findByStatus(PayoutStatus status);

    List<Payout> findBySellerId(String sellerId);
}
