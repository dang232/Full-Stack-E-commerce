package com.vnshop.orderservice.infrastructure.persistence.finance;

import com.vnshop.orderservice.domain.finance.Payout;
import com.vnshop.orderservice.domain.finance.PayoutStatus;
import com.vnshop.orderservice.domain.finance.port.out.PayoutRepositoryPort;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class PayoutJpaRepository implements PayoutRepositoryPort {
    private final PayoutSpringDataRepository repository;

    public PayoutJpaRepository(PayoutSpringDataRepository repository) {
        this.repository = repository;
    }

    @Override
    public Payout save(Payout payout) {
        return repository.save(PayoutJpaEntity.fromDomain(payout)).toDomain();
    }

    @Override
    public Optional<Payout> findById(UUID payoutId) {
        return repository.findById(payoutId).map(PayoutJpaEntity::toDomain);
    }

    @Override
    public List<Payout> findByStatus(PayoutStatus status) {
        return repository.findByStatus(status).stream().map(PayoutJpaEntity::toDomain).toList();
    }

    @Override
    public List<Payout> findBySellerId(String sellerId) {
        return repository.findBySellerId(sellerId).stream().map(PayoutJpaEntity::toDomain).toList();
    }
}
