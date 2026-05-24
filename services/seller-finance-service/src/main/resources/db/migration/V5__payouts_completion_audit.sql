-- Audit trail for the admin who completed a payout. Both columns are
-- nullable: rows still in PENDING/FAILED states have nothing to record,
-- and historical COMPLETED rows that predate this migration have no
-- captured admin identity. The UI renders an em-dash when these are null.
ALTER TABLE seller_finance_svc.payouts
    ADD COLUMN IF NOT EXISTS completed_by VARCHAR(255),
    ADD COLUMN IF NOT EXISTS completed_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX IF NOT EXISTS idx_payouts_completed_at
    ON seller_finance_svc.payouts (completed_at)
    WHERE completed_at IS NOT NULL;
