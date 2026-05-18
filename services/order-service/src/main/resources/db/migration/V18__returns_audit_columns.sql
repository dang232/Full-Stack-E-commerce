-- V18: backfill audit columns on order_svc.returns. ReturnJpaEntity extends
-- BaseJpaEntity which expects created_at/updated_at on every row, but V4
-- created the returns table before the @MappedSuperclass refactor and V17
-- only patched orders/sub_orders/order_items. Without this, every SELECT on
-- returns blows up with `column rje1_0.created_at does not exist`, which
-- silently broke the entire return + refund saga compensation path.
--
-- disputes already has the columns (a later migration added them); this
-- migration is idempotent on disputes so the file stays a single change set.

ALTER TABLE order_svc.returns
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;

UPDATE order_svc.returns
   SET created_at = COALESCE(requested_at, NOW())
 WHERE created_at IS NULL;

UPDATE order_svc.returns
   SET updated_at = COALESCE(resolved_at, requested_at, NOW())
 WHERE updated_at IS NULL;

ALTER TABLE order_svc.returns
    ALTER COLUMN created_at SET NOT NULL,
    ALTER COLUMN updated_at SET NOT NULL;

ALTER TABLE order_svc.disputes
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;

UPDATE order_svc.disputes
   SET created_at = NOW()
 WHERE created_at IS NULL;

UPDATE order_svc.disputes
   SET updated_at = NOW()
 WHERE updated_at IS NULL;

ALTER TABLE order_svc.disputes
    ALTER COLUMN created_at SET NOT NULL,
    ALTER COLUMN updated_at SET NOT NULL;
