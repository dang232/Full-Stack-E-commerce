-- BaseJpaEntity (extended by PayoutJpaEntity) declares created_at + updated_at,
-- but V2 only created payouts.created_at. Backfill the missing audit column so
-- Hibernate's strict schema validation stops failing on every payouts query.
ALTER TABLE seller_finance_svc.payouts
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE;

UPDATE seller_finance_svc.payouts
   SET updated_at = COALESCE(updated_at, created_at, NOW())
 WHERE updated_at IS NULL;
