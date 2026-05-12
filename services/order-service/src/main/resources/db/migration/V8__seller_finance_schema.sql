CREATE TABLE IF NOT EXISTS order_svc.seller_wallets (
    seller_id VARCHAR(255) NOT NULL PRIMARY KEY,
    available_balance NUMERIC(19, 2) NOT NULL,
    pending_balance NUMERIC(19, 2) NOT NULL,
    total_earned NUMERIC(19, 2) NOT NULL,
    total_fees NUMERIC(19, 2) NOT NULL,
    total_withdrawn NUMERIC(19, 2) NOT NULL,
    last_payout_at TIMESTAMP,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT chk_seller_wallets_available_non_negative CHECK (available_balance >= 0),
    CONSTRAINT chk_seller_wallets_pending_non_negative CHECK (pending_balance >= 0),
    CONSTRAINT chk_seller_wallets_total_earned_non_negative CHECK (total_earned >= 0),
    CONSTRAINT chk_seller_wallets_total_fees_non_negative CHECK (total_fees >= 0),
    CONSTRAINT chk_seller_wallets_total_withdrawn_non_negative CHECK (total_withdrawn >= 0)
);

CREATE TABLE IF NOT EXISTS order_svc.seller_transactions (
    transaction_id uuid NOT NULL PRIMARY KEY,
    seller_id VARCHAR(255) NOT NULL,
    type VARCHAR(32) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    fee_amount NUMERIC(19, 2) NOT NULL,
    balance_after NUMERIC(19, 2) NOT NULL,
    idempotency_key VARCHAR(512) NOT NULL UNIQUE,
    transaction_created_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT chk_seller_transactions_amount_non_negative CHECK (amount >= 0),
    CONSTRAINT chk_seller_transactions_fee_non_negative CHECK (fee_amount >= 0),
    CONSTRAINT chk_seller_transactions_balance_non_negative CHECK (balance_after >= 0)
);

CREATE TABLE IF NOT EXISTS order_svc.payouts (
    payout_id uuid NOT NULL PRIMARY KEY,
    seller_id VARCHAR(255) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    status VARCHAR(32) NOT NULL,
    payout_created_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT chk_payouts_amount_positive CHECK (amount > 0)
);

CREATE INDEX IF NOT EXISTS idx_seller_transactions_seller_created ON order_svc.seller_transactions (seller_id, transaction_created_at);
CREATE INDEX IF NOT EXISTS idx_payouts_seller_status ON order_svc.payouts (seller_id, status);
CREATE INDEX IF NOT EXISTS idx_payouts_status ON order_svc.payouts (status);
