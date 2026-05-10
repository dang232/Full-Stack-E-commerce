package com.vnshop.sellerfinanceservice.domain.port.out;

import com.vnshop.sellerfinanceservice.domain.Payout;
import com.vnshop.sellerfinanceservice.domain.PayoutStatus;

import java.util.List;
import java.util.Optional;

public interface PayoutRepositoryPort {
    Payout save(Payout payout);

    Optional<Payout> findById(String payoutId);

    List<Payout> findByStatus(PayoutStatus status);

    List<Payout> findBySellerId(String sellerId);
}
