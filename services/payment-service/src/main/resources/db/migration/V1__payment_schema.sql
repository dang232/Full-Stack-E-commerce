CREATE SCHEMA IF NOT EXISTS payment_svc;

CREATE TABLE IF NOT EXISTS payment_svc.payments (
    payment_id VARCHAR(255) PRIMARY KEY,
    order_id VARCHAR(255) NOT NULL UNIQUE,
    buyer_id VARCHAR(255) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    method VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    transaction_ref VARCHAR(1024),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_payments_order_id ON payment_svc.payments (order_id);
CREATE INDEX IF NOT EXISTS idx_payments_buyer_id ON payment_svc.payments (buyer_id);
