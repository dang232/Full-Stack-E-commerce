-- Add the audit columns the BaseJpaEntity inherits expects on every order_svc
-- table. The original V1 schema predates the @MappedSuperclass refactor that
-- pulled created_at / updated_at out of every entity into BaseJpaEntity, so a
-- plain SELECT now blows up with `column o1_0.updated_at does not exist`.
-- Backfill existing rows to NOW() so the not-null constraint can be added.

ALTER TABLE order_svc.orders
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;

UPDATE order_svc.orders
   SET created_at = NOW()
 WHERE created_at IS NULL;

UPDATE order_svc.orders
   SET updated_at = NOW()
 WHERE updated_at IS NULL;

ALTER TABLE order_svc.orders
    ALTER COLUMN created_at SET NOT NULL,
    ALTER COLUMN updated_at SET NOT NULL;

ALTER TABLE order_svc.sub_orders
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;

UPDATE order_svc.sub_orders
   SET created_at = NOW()
 WHERE created_at IS NULL;

UPDATE order_svc.sub_orders
   SET updated_at = NOW()
 WHERE updated_at IS NULL;

ALTER TABLE order_svc.sub_orders
    ALTER COLUMN created_at SET NOT NULL,
    ALTER COLUMN updated_at SET NOT NULL;

ALTER TABLE order_svc.order_items
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;

UPDATE order_svc.order_items
   SET created_at = NOW()
 WHERE created_at IS NULL;

UPDATE order_svc.order_items
   SET updated_at = NOW()
 WHERE updated_at IS NULL;

ALTER TABLE order_svc.order_items
    ALTER COLUMN created_at SET NOT NULL,
    ALTER COLUMN updated_at SET NOT NULL;
