-- V8: backfill audit columns on payment_svc tables. Same root cause as V17
-- on order-service and V18 on order-service returns: BaseJpaEntity gained
-- updated_at after these tables were created, so SELECTs now blow up with
-- `column updated_at does not exist`. Affected tables (every entity that
-- extends BaseJpaEntity in payment-service):
--   - payments
--   - ledger_entries
--   - payment_callback_logs
--   - payment_callback_outbox
--   - reconciliation_issues
--
-- Idempotent — ADD COLUMN IF NOT EXISTS, backfill from created_at, ALTER
-- COLUMN SET NOT NULL once values are populated.

ALTER TABLE payment_svc.payments
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE;
UPDATE payment_svc.payments SET updated_at = COALESCE(created_at, NOW()) WHERE updated_at IS NULL;
ALTER TABLE payment_svc.payments ALTER COLUMN updated_at SET NOT NULL;

ALTER TABLE payment_svc.ledger_entries
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE;
UPDATE payment_svc.ledger_entries SET updated_at = COALESCE(created_at, NOW()) WHERE updated_at IS NULL;
ALTER TABLE payment_svc.ledger_entries ALTER COLUMN updated_at SET NOT NULL;

ALTER TABLE payment_svc.payment_callback_logs
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE;
UPDATE payment_svc.payment_callback_logs SET created_at = NOW() WHERE created_at IS NULL;
UPDATE payment_svc.payment_callback_logs SET updated_at = NOW() WHERE updated_at IS NULL;
ALTER TABLE payment_svc.payment_callback_logs
    ALTER COLUMN created_at SET NOT NULL,
    ALTER COLUMN updated_at SET NOT NULL;

ALTER TABLE payment_svc.payment_callback_outbox
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE;
UPDATE payment_svc.payment_callback_outbox SET created_at = NOW() WHERE created_at IS NULL;
UPDATE payment_svc.payment_callback_outbox SET updated_at = NOW() WHERE updated_at IS NULL;
ALTER TABLE payment_svc.payment_callback_outbox
    ALTER COLUMN created_at SET NOT NULL,
    ALTER COLUMN updated_at SET NOT NULL;

ALTER TABLE payment_svc.reconciliation_issues
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE;
UPDATE payment_svc.reconciliation_issues SET created_at = NOW() WHERE created_at IS NULL;
UPDATE payment_svc.reconciliation_issues SET updated_at = NOW() WHERE updated_at IS NULL;
ALTER TABLE payment_svc.reconciliation_issues
    ALTER COLUMN created_at SET NOT NULL,
    ALTER COLUMN updated_at SET NOT NULL;
