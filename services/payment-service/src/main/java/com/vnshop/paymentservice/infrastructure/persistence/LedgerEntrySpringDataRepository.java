package com.vnshop.paymentservice.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;

interface LedgerEntrySpringDataRepository extends Repository<LedgerEntryJpaEntity, Long> {
    List<LedgerEntryJpaEntity> saveAll(Iterable<LedgerEntryJpaEntity> ledgerEntries);

    List<LedgerEntryJpaEntity> findByOrderId(String orderId);

    List<LedgerEntryJpaEntity> findByJournalId(UUID journalId);
}
