-- Add device_fingerprint column to orders table for fraud audit trail
ALTER TABLE order_svc.orders
    ADD COLUMN IF NOT EXISTS device_fingerprint VARCHAR(255);

-- Add FLAGGED as a valid payment_status value
-- (enum is stored as VARCHAR; no constraint change needed for PostgreSQL string columns)

-- Index for fraud velocity query: count recent orders per buyer
CREATE INDEX IF NOT EXISTS idx_orders_buyer_created
    ON order_svc.orders (buyer_id, created_at DESC);
