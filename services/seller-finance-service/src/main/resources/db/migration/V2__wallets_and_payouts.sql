CREATE SCHEMA IF NOT EXISTS seller_finance_svc;

CREATE TABLE IF NOT EXISTS seller_finance_svc.seller_wallets (
    seller_id VARCHAR(255) PRIMARY KEY,
    available_balance NUMERIC(19, 2) NOT NULL,
    pending_balance NUMERIC(19, 2) NOT NULL,
    total_earned NUMERIC(19, 2) NOT NULL,
    last_payout_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_seller_wallets_available_non_negative CHECK (available_balance >= 0),
    CONSTRAINT chk_seller_wallets_pending_non_negative CHECK (pending_balance >= 0),
    CONSTRAINT chk_seller_wallets_total_earned_non_negative CHECK (total_earned >= 0)
);

CREATE TABLE IF NOT EXISTS seller_finance_svc.payouts (
    payout_id uuid NOT NULL PRIMARY KEY,
    seller_id VARCHAR(255) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_payouts_seller_wallets
        FOREIGN KEY (seller_id)
        REFERENCES seller_finance_svc.seller_wallets (seller_id),
    CONSTRAINT chk_payouts_amount_positive CHECK (amount > 0)
);

CREATE INDEX IF NOT EXISTS idx_payouts_seller_id ON seller_finance_svc.payouts (seller_id);
CREATE INDEX IF NOT EXISTS idx_payouts_status ON seller_finance_svc.payouts (status);
