-- Outbox retry, backoff, and dead-letter fields
ALTER TABLE order_svc.outbox_events
    ADD COLUMN IF NOT EXISTS attempt_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS next_attempt_at TIMESTAMP NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS last_error TEXT;

-- Index for picking events ready for retry
CREATE INDEX IF NOT EXISTS idx_outbox_events_status_next_attempt
    ON order_svc.outbox_events (status, next_attempt_at)
    WHERE status = 'PENDING';

-- Saga compensation timeout tracking (re-use updated_at as compensation_started_at)
-- No schema change needed - updated_at already exists in saga_state
