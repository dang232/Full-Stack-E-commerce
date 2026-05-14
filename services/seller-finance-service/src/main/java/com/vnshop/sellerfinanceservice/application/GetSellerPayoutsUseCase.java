package com.vnshop.sellerfinanceservice.application;

import com.vnshop.sellerfinanceservice.domain.Payout;
import com.vnshop.sellerfinanceservice.domain.port.out.PayoutRepositoryPort;

import java.util.List;
import java.util.Objects;

public class GetSellerPayoutsUseCase {

    private final PayoutRepositoryPort payoutRepository;

    public GetSellerPayoutsUseCase(PayoutRepositoryPort payoutRepository) {
        this.payoutRepository = Objects.requireNonNull(payoutRepository, "payoutRepository is required");
    }

    public List<Payout> bySellerId(String sellerId) {
        requireNonBlank(sellerId, "sellerId");
        return payoutRepository.findBySellerId(sellerId);
    }

    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }
}
