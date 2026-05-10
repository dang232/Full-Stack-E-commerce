package com.vnshop.paymentservice.infrastructure.persistence;

import com.vnshop.paymentservice.domain.JournalEntry;
import com.vnshop.paymentservice.domain.LedgerEntry;
import com.vnshop.paymentservice.domain.port.out.LedgerRepositoryPort;
import org.springframework.data.repository.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@org.springframework.stereotype.Repository
public class LedgerEntryJpaRepository implements LedgerRepositoryPort {
    private final LedgerEntrySpringDataRepository springDataRepository;

    public LedgerEntryJpaRepository(LedgerEntrySpringDataRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    @Transactional
    public List<LedgerEntry> append(JournalEntry journalEntry) {
        List<LedgerEntryJpaEntity> entities = journalEntry.postings().stream()
                .map(posting -> LedgerEntry.fromJournalPosting(journalEntry, posting))
                .map(LedgerEntryJpaEntity::fromDomain)
                .toList();
        return springDataRepository.saveAll(entities).stream()
                .map(LedgerEntryJpaEntity::toDomain)
                .toList();
    }

    @Override
    public List<LedgerEntry> findByOrderId(String orderId) {
        return springDataRepository.findByOrderId(orderId).stream()
                .map(LedgerEntryJpaEntity::toDomain)
                .toList();
    }

    @Override
    public List<LedgerEntry> findByJournalId(String journalId) {
        return springDataRepository.findByJournalId(journalId).stream()
                .map(LedgerEntryJpaEntity::toDomain)
                .toList();
    }
}

interface LedgerEntrySpringDataRepository extends Repository<LedgerEntryJpaEntity, Long> {
    List<LedgerEntryJpaEntity> saveAll(Iterable<LedgerEntryJpaEntity> ledgerEntries);

    List<LedgerEntryJpaEntity> findByOrderId(String orderId);

    List<LedgerEntryJpaEntity> findByJournalId(String journalId);
}
