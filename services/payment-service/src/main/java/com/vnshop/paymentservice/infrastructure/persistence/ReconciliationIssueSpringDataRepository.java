package com.vnshop.paymentservice.infrastructure.persistence;

import org.springframework.data.repository.Repository;

interface ReconciliationIssueSpringDataRepository extends Repository<ReconciliationIssueJpaEntity, Long> {
    ReconciliationIssueJpaEntity save(ReconciliationIssueJpaEntity issue);
}
