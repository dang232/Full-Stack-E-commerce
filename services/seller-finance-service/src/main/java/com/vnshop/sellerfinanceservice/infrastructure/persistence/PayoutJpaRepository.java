package com.vnshop.sellerfinanceservice.infrastructure.persistence;

import com.vnshop.sellerfinanceservice.domain.Payout;
import com.vnshop.sellerfinanceservice.domain.PayoutStatus;
import com.vnshop.sellerfinanceservice.domain.port.out.PayoutRepositoryPort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

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
    public Optional<Payout> findById(String payoutId) {
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

    interface PayoutSpringDataRepository extends JpaRepository<PayoutJpaEntity, String> {
        List<PayoutJpaEntity> findByStatus(PayoutStatus status);

        List<PayoutJpaEntity> findBySellerId(String sellerId);
    }
}
