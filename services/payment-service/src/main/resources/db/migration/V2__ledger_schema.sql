CREATE TABLE IF NOT EXISTS payment_svc.ledger_entries (
    ledger_entry_id BIGSERIAL PRIMARY KEY,
    transaction_id VARCHAR(255) NOT NULL,
    order_id VARCHAR(255) NOT NULL,
    debit_account VARCHAR(255) NOT NULL,
    credit_account VARCHAR(255) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'VND',
    status VARCHAR(32) NOT NULL,
    description VARCHAR(1024),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (transaction_id, debit_account, credit_account)
);

CREATE INDEX IF NOT EXISTS idx_ledger_entries_order_id ON payment_svc.ledger_entries (order_id);
CREATE INDEX IF NOT EXISTS idx_ledger_entries_transaction_id ON payment_svc.ledger_entries (transaction_id);
