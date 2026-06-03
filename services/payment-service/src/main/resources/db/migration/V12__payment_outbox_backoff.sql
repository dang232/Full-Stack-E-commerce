ALTER TABLE payment_svc.payment_callback_outbox ADD COLUMN IF NOT EXISTS attempt_count INT NOT NULL DEFAULT 0;
ALTER TABLE payment_svc.payment_callback_outbox ADD COLUMN IF NOT EXISTS next_attempt_at TIMESTAMPTZ;
ALTER TABLE payment_svc.payment_callback_outbox ADD COLUMN IF NOT EXISTS last_error TEXT;
ALTER TABLE payment_svc.payment_callback_outbox ADD COLUMN IF NOT EXISTS dead BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_payment_callback_outbox_pending
    ON payment_svc.payment_callback_outbox (dead, published_at, next_attempt_at)
    WHERE dead = FALSE AND published_at IS NULL;
