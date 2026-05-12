package com.vnshop.paymentservice.infrastructure.persistence;

import com.vnshop.paymentservice.domain.ReconciliationIssue;
import com.vnshop.paymentservice.domain.port.out.ReconciliationIssueRepositoryPort;
@org.springframework.stereotype.Repository
public class ReconciliationIssueJpaRepository implements ReconciliationIssueRepositoryPort {
    private final ReconciliationIssueSpringDataRepository springDataRepository;

    public ReconciliationIssueJpaRepository(ReconciliationIssueSpringDataRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public ReconciliationIssue save(ReconciliationIssue issue) {
        return springDataRepository.save(ReconciliationIssueJpaEntity.fromDomain(issue)).toDomain();
    }
}
