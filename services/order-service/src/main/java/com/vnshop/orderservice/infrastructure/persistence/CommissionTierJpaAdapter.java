package com.vnshop.orderservice.infrastructure.persistence;

import com.vnshop.orderservice.domain.CommissionTier;
import com.vnshop.orderservice.domain.port.out.CommissionTierLookupPort;
import org.springframework.stereotype.Component;

@Component
public class CommissionTierJpaAdapter implements CommissionTierLookupPort {
    private final SellerCommissionTierRepository repository;

    public CommissionTierJpaAdapter(SellerCommissionTierRepository repository) {
        this.repository = repository;
    }

    @Override
    public CommissionTier findBySellerId(String sellerId) {
        return repository.findById(sellerId)
            .map(SellerCommissionTierJpaEntity::tier)
            .orElse(CommissionTier.STANDARD);
    }
}
