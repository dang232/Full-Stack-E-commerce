CREATE TABLE IF NOT EXISTS payment_svc.processed_webhooks (
    webhook_id   VARCHAR(255) NOT NULL,
    provider     VARCHAR(32)  NOT NULL,
    event_type   VARCHAR(128) NOT NULL,
    processed_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_processed_webhooks PRIMARY KEY (webhook_id, provider)
);

CREATE INDEX IF NOT EXISTS idx_processed_webhooks_processed_at
    ON payment_svc.processed_webhooks (processed_at);

CREATE TABLE IF NOT EXISTS payment_svc.pending_webhooks (
    id            UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    webhook_id    VARCHAR(255) NOT NULL,
    provider      VARCHAR(32)  NOT NULL,
    event_type    VARCHAR(128) NOT NULL,
    payload       TEXT         NOT NULL,
    attempts      INT          NOT NULL DEFAULT 0,
    max_attempts  INT          NOT NULL DEFAULT 3,
    next_retry_at TIMESTAMPTZ,
    status        VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_pending_webhooks_status_next_retry
    ON payment_svc.pending_webhooks (status, next_retry_at)
    WHERE status = 'PENDING';
