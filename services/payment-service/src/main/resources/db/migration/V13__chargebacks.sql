CREATE TABLE IF NOT EXISTS payment_svc.chargebacks (
    id                     BIGSERIAL PRIMARY KEY,
    chargeback_id          UUID         NOT NULL UNIQUE,
    order_id               VARCHAR(64)  NOT NULL,
    external_chargeback_id VARCHAR(255) NOT NULL UNIQUE,
    provider               VARCHAR(16)  NOT NULL,
    reason                 VARCHAR(512) NOT NULL,
    status                 VARCHAR(16)  NOT NULL DEFAULT 'OPEN',
    evidence_json          TEXT,
    due_date               DATE,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_chargebacks_order_id
    ON payment_svc.chargebacks (order_id);

CREATE INDEX IF NOT EXISTS idx_chargebacks_status
    ON payment_svc.chargebacks (status);
