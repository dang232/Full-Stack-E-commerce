package com.vnshop.sellerfinanceservice.application;

import com.vnshop.sellerfinanceservice.domain.Payout;
import com.vnshop.sellerfinanceservice.domain.port.out.PayoutRepositoryPort;

import java.util.List;

public class ListPayoutsUseCase {

    private final PayoutRepositoryPort payoutRepository;

    public ListPayoutsUseCase(PayoutRepositoryPort payoutRepository) {
        this.payoutRepository = payoutRepository;
    }

    public List<Payout> listBySellerId(String sellerId) {
        return payoutRepository.findBySellerId(sellerId);
    }
}
