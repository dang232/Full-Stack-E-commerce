CREATE TABLE IF NOT EXISTS payment_svc.payment_idempotency_keys (
    idempotency_key VARCHAR(255) NOT NULL PRIMARY KEY,
    payment_id uuid NOT NULL REFERENCES payment_svc.payments (payment_id),
    request_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_payment_idempotency_keys_created_at ON payment_svc.payment_idempotency_keys (created_at);
CREATE INDEX IF NOT EXISTS idx_payment_idempotency_keys_payment_id ON payment_svc.payment_idempotency_keys (payment_id);
