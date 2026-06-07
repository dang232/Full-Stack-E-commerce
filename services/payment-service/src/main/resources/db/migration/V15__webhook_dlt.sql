-- Webhook Dead Letter Table: stores webhooks that failed 3+ times
CREATE TABLE IF NOT EXISTS payment_svc.webhook_dead_letter (
    id              UUID        PRIMARY KEY,
    webhook_id      VARCHAR(255) NOT NULL,
    provider        VARCHAR(64)  NOT NULL,
    event_type      VARCHAR(128) NOT NULL,
    payload         TEXT         NOT NULL,
    failure_reason  TEXT,
    attempts        INT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    retried_at      TIMESTAMPTZ,
    retry_count     INT          NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_webhook_dlt_provider ON payment_svc.webhook_dead_letter (provider);
CREATE INDEX IF NOT EXISTS idx_webhook_dlt_created_at ON payment_svc.webhook_dead_letter (created_at DESC);
