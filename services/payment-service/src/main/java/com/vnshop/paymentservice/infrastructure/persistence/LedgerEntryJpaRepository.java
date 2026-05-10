package com.vnshop.paymentservice.infrastructure.persistence;

import com.vnshop.paymentservice.domain.LedgerEntry;
import com.vnshop.paymentservice.domain.port.out.LedgerRepositoryPort;
import org.springframework.data.repository.Repository;

import java.util.List;

@org.springframework.stereotype.Repository
public class LedgerEntryJpaRepository implements LedgerRepositoryPort {
    private final LedgerEntrySpringDataRepository springDataRepository;

    public LedgerEntryJpaRepository(LedgerEntrySpringDataRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public LedgerEntry save(LedgerEntry ledgerEntry) {
        return springDataRepository.save(LedgerEntryJpaEntity.fromDomain(ledgerEntry)).toDomain();
    }

    @Override
    public List<LedgerEntry> findByOrderId(String orderId) {
        return springDataRepository.findByOrderId(orderId).stream()
                .map(LedgerEntryJpaEntity::toDomain)
                .toList();
    }
}

interface LedgerEntrySpringDataRepository extends Repository<LedgerEntryJpaEntity, Long> {
    LedgerEntryJpaEntity save(LedgerEntryJpaEntity ledgerEntry);

    List<LedgerEntryJpaEntity> findByOrderId(String orderId);
}
