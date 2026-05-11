ALTER TABLE order_svc.order_items
    ADD COLUMN IF NOT EXISTS seller_id VARCHAR(255);

UPDATE order_svc.order_items item
SET seller_id = sub_order.seller_id
FROM order_svc.sub_orders sub_order
WHERE item.sub_order_id = sub_order.id
  AND item.seller_id IS NULL;

ALTER TABLE order_svc.order_items
    ALTER COLUMN seller_id SET NOT NULL;

CREATE TABLE IF NOT EXISTS order_svc.returns (
    return_id uuid NOT NULL PRIMARY KEY,
    order_id uuid NOT NULL,
    sub_order_id BIGINT NOT NULL,
    buyer_id VARCHAR(255) NOT NULL,
    reason VARCHAR(2048) NOT NULL,
    status VARCHAR(32) NOT NULL,
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL,
    resolved_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_returns_orders
        FOREIGN KEY (order_id)
        REFERENCES order_svc.orders (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_returns_sub_orders
        FOREIGN KEY (sub_order_id)
        REFERENCES order_svc.sub_orders (id)
        ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS order_svc.disputes (
    dispute_id uuid NOT NULL PRIMARY KEY,
    return_id uuid NOT NULL,
    buyer_reason VARCHAR(2048) NOT NULL,
    seller_response VARCHAR(2048),
    admin_resolution VARCHAR(2048),
    status VARCHAR(32) NOT NULL,
    CONSTRAINT fk_disputes_returns
        FOREIGN KEY (return_id)
        REFERENCES order_svc.returns (return_id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_returns_buyer_id ON order_svc.returns (buyer_id);
CREATE INDEX IF NOT EXISTS idx_returns_sub_order_id ON order_svc.returns (sub_order_id);
CREATE INDEX IF NOT EXISTS idx_disputes_status ON order_svc.disputes (status);
