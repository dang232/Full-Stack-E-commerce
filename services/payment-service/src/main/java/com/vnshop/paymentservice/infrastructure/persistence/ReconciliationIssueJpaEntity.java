package com.vnshop.paymentservice.infrastructure.persistence;

import com.vnshop.paymentservice.domain.ReconciliationIssue;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(schema = "payment_svc", name = "reconciliation_issues")
public class ReconciliationIssueJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "issue_id")
    private Long issueId;

    @Column(name = "payment_id", nullable = false)
    private String paymentId;

    @Column(name = "expected_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal expectedAmount;

    @Column(name = "actual_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal actualAmount;

    @Column(name = "description", nullable = false, length = 1024)
    private String description;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    @Column(name = "resolved", nullable = false)
    private boolean resolved;

    protected ReconciliationIssueJpaEntity() {
    }

    public static ReconciliationIssueJpaEntity fromDomain(ReconciliationIssue issue) {
        ReconciliationIssueJpaEntity entity = new ReconciliationIssueJpaEntity();
        entity.issueId = issue.issueId();
        entity.paymentId = issue.paymentId();
        entity.expectedAmount = issue.expectedAmount();
        entity.actualAmount = issue.actualAmount();
        entity.description = issue.description();
        entity.detectedAt = issue.detectedAt();
        entity.resolved = issue.resolved();
        return entity;
    }

    public ReconciliationIssue toDomain() {
        return new ReconciliationIssue(issueId, paymentId, expectedAmount, actualAmount, description, detectedAt, resolved);
    }
}
