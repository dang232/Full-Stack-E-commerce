-- V10: SePay polling cursor. The poller reads transactions newer than the
-- last seen tx id and advances the cursor on each successful poll, so a
-- restart doesn't replay every credit since the dawn of the bank account.
-- Singleton row enforced via CHECK (id = 1) — the table holds exactly one
-- cursor for the configured account.

CREATE TABLE IF NOT EXISTS payment_svc.sepay_cursor (
    id INT PRIMARY KEY DEFAULT 1,
    last_tx_id VARCHAR(64),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT sepay_cursor_singleton CHECK (id = 1)
);

INSERT INTO payment_svc.sepay_cursor (id) VALUES (1) ON CONFLICT DO NOTHING;
