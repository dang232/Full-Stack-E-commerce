-- Optimistic lock for saga_state to prevent lost updates between concurrent step handlers
-- and the timeout finalizer.
ALTER TABLE order_svc.saga_state
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
