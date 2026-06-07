-- V16: Add PAYMENT_TIMEOUT to the payments status check constraint.
-- PaymentStatus enum already includes PAYMENT_TIMEOUT in the application layer;
-- this migration relaxes the DB-level check so the new value can be persisted.
-- The status column is VARCHAR(32) with an existing CHECK constraint added in V1.

ALTER TABLE payment_svc.payments
    DROP CONSTRAINT IF EXISTS payments_status_check;

ALTER TABLE payment_svc.payments
    ADD CONSTRAINT payments_status_check
        CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED', 'REFUNDED', 'PAYMENT_TIMEOUT'));
