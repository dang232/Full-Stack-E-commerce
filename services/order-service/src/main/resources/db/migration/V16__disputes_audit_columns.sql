-- BaseJpaEntity expects every persisted table to carry created_at + updated_at, but
-- the original V4 migration created order_svc.disputes without them. Backfill so
-- Hibernate's strict-schema validation stops crashing on every dispute query.
ALTER TABLE order_svc.disputes
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;

UPDATE order_svc.disputes
   SET created_at = COALESCE(created_at, NOW()),
       updated_at = COALESCE(updated_at, NOW())
 WHERE created_at IS NULL OR updated_at IS NULL;
