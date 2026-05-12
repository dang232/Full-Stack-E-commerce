package com.vnshop.orderservice.application.finance;

import com.vnshop.orderservice.domain.finance.Payout;
import com.vnshop.orderservice.domain.finance.port.out.PayoutRepositoryPort;

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
