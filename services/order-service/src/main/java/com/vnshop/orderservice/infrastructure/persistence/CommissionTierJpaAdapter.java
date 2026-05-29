package com.vnshop.orderservice.infrastructure.persistence;

import com.vnshop.orderservice.domain.CommissionTier;
import com.vnshop.orderservice.domain.port.out.CommissionTierLookupPort;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class CommissionTierJpaAdapter implements CommissionTierLookupPort {
    private final SellerCommissionTierRepository repository;

    public CommissionTierJpaAdapter(SellerCommissionTierRepository repository) {
        this.repository = repository;
    }

    @Override
    public CommissionTier findBySellerId(String sellerId) {
        return findBySellerIds(Set.of(sellerId))
                .getOrDefault(sellerId, CommissionTier.STANDARD);
    }

    @Override
    public Map<String, CommissionTier> findBySellerIds(Set<String> sellerIds) {
        return repository.findAllById(sellerIds).stream()
                .collect(Collectors.toMap(
                        SellerCommissionTierJpaEntity::sellerId,
                        SellerCommissionTierJpaEntity::tier
                ));
    }
}
