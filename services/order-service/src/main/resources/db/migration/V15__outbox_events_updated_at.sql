-- BaseJpaEntity (which OutboxEventJpaEntity extends) declares an updated_at column,
-- but the original V2 schema only created created_at. Hibernate's strict-schema mode
-- crashes on every outbox poll. Backfill the missing column and any rows.
ALTER TABLE order_svc.outbox_events
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;

UPDATE order_svc.outbox_events
   SET updated_at = COALESCE(updated_at, created_at, NOW())
 WHERE updated_at IS NULL;
