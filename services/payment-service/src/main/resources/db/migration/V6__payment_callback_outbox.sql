CREATE TABLE IF NOT EXISTS payment_svc.payment_callback_outbox (
    id BIGSERIAL PRIMARY KEY,
    provider VARCHAR(32) NOT NULL,
    payment_id VARCHAR(255) NOT NULL,
    order_id VARCHAR(255) NOT NULL,
    transaction_ref VARCHAR(1024) NOT NULL,
    status VARCHAR(32) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'VND',
    callback_id VARCHAR(255) NOT NULL,
    callback_event_id VARCHAR(255),
    payload_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_payment_callback_outbox_callback
        FOREIGN KEY (callback_id) REFERENCES payment_svc.payment_callback_logs (callback_id)
);

CREATE INDEX IF NOT EXISTS idx_payment_callback_outbox_payment_id ON payment_svc.payment_callback_outbox (payment_id);
CREATE INDEX IF NOT EXISTS idx_payment_callback_outbox_created_at ON payment_svc.payment_callback_outbox (created_at);
CREATE UNIQUE INDEX IF NOT EXISTS ux_payment_callback_outbox_provider_payload ON payment_svc.payment_callback_outbox (provider, payload_hash);
