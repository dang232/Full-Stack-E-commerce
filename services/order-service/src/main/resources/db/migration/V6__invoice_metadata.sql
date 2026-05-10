CREATE TABLE IF NOT EXISTS order_svc.invoices (
    id VARCHAR(255) PRIMARY KEY,
    order_id VARCHAR(255) NOT NULL,
    sub_order_id BIGINT NOT NULL UNIQUE,
    buyer_id VARCHAR(255) NOT NULL,
    seller_id VARCHAR(255) NOT NULL,
    object_key VARCHAR(1024) NOT NULL UNIQUE,
    checksum_sha256 VARCHAR(64) NOT NULL,
    version INTEGER NOT NULL,
    generated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_invoices_orders
        FOREIGN KEY (order_id)
        REFERENCES order_svc.orders (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_invoices_sub_orders
        FOREIGN KEY (sub_order_id)
        REFERENCES order_svc.sub_orders (id)
        ON DELETE CASCADE,
    CONSTRAINT chk_invoices_version_positive CHECK (version > 0)
);

CREATE INDEX IF NOT EXISTS idx_invoices_buyer_id ON order_svc.invoices (buyer_id);
CREATE INDEX IF NOT EXISTS idx_invoices_seller_id ON order_svc.invoices (seller_id);
