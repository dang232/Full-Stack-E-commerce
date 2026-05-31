CREATE TABLE IF NOT EXISTS order_svc.order_summary (
    order_id VARCHAR(36) PRIMARY KEY,
    buyer_id VARCHAR(36),
    seller_id VARCHAR(36),
    status VARCHAR(30) NOT NULL,
    total_amount DECIMAL(12, 2),
    item_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
