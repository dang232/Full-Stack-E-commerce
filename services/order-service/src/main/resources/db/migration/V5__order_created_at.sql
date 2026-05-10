ALTER TABLE order_svc.orders
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT now();

CREATE INDEX IF NOT EXISTS idx_orders_created_at ON order_svc.orders (created_at);
