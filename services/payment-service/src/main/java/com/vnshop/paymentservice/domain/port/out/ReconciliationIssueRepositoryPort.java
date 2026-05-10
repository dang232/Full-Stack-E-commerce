package com.vnshop.paymentservice.domain.port.out;

import com.vnshop.paymentservice.domain.ReconciliationIssue;

public interface ReconciliationIssueRepositoryPort {
    ReconciliationIssue save(ReconciliationIssue issue);
}
