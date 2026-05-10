CREATE TABLE IF NOT EXISTS payment_svc.reconciliation_issues (
    issue_id BIGSERIAL PRIMARY KEY,
    payment_id VARCHAR(255) NOT NULL,
    expected_amount NUMERIC(19, 2) NOT NULL,
    actual_amount NUMERIC(19, 2) NOT NULL,
    description VARCHAR(1024) NOT NULL,
    detected_at TIMESTAMP WITH TIME ZONE NOT NULL,
    resolved BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_reconciliation_issues_payment_id ON payment_svc.reconciliation_issues (payment_id);
CREATE INDEX IF NOT EXISTS idx_reconciliation_issues_resolved ON payment_svc.reconciliation_issues (resolved);
