CREATE SCHEMA IF NOT EXISTS order_svc;

CREATE TABLE IF NOT EXISTS order_svc.orders (
    id VARCHAR(255) PRIMARY KEY,
    order_number VARCHAR(255) NOT NULL UNIQUE,
    buyer_id VARCHAR(255) NOT NULL,
    shipping_street VARCHAR(255) NOT NULL,
    shipping_ward VARCHAR(255),
    shipping_district VARCHAR(255) NOT NULL,
    shipping_city VARCHAR(255) NOT NULL,
    items_total_amount NUMERIC(19, 0) NOT NULL,
    items_total_currency VARCHAR(8) NOT NULL,
    shipping_total_amount NUMERIC(19, 0) NOT NULL,
    shipping_total_currency VARCHAR(8) NOT NULL,
    discount_amount NUMERIC(19, 0) NOT NULL,
    discount_currency VARCHAR(8) NOT NULL,
    final_amount NUMERIC(19, 0) NOT NULL,
    final_currency VARCHAR(8) NOT NULL,
    payment_method VARCHAR(64) NOT NULL,
    payment_status VARCHAR(32) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS order_svc.sub_orders (
    id BIGSERIAL PRIMARY KEY,
    order_id VARCHAR(255) NOT NULL,
    seller_id VARCHAR(255) NOT NULL,
    fulfillment_status VARCHAR(32) NOT NULL,
    shipping_cost_amount NUMERIC(19, 0) NOT NULL,
    shipping_cost_currency VARCHAR(8) NOT NULL,
    shipping_method VARCHAR(64) NOT NULL,
    carrier VARCHAR(255),
    tracking_number VARCHAR(255),
    CONSTRAINT fk_sub_orders_orders
        FOREIGN KEY (order_id)
        REFERENCES order_svc.orders (id)
        ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS order_svc.order_items (
    id BIGSERIAL PRIMARY KEY,
    sub_order_id BIGINT NOT NULL,
    product_id VARCHAR(255) NOT NULL,
    variant_sku VARCHAR(255) NOT NULL,
    seller_id VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    quantity INTEGER NOT NULL,
    unit_price_amount NUMERIC(19, 0) NOT NULL,
    unit_price_currency VARCHAR(8) NOT NULL,
    image_url VARCHAR(1024),
    CONSTRAINT fk_order_items_sub_orders
        FOREIGN KEY (sub_order_id)
        REFERENCES order_svc.sub_orders (id)
        ON DELETE CASCADE,
    CONSTRAINT chk_order_items_quantity_positive CHECK (quantity > 0)
);

CREATE INDEX IF NOT EXISTS idx_orders_buyer_id ON order_svc.orders (buyer_id);
CREATE INDEX IF NOT EXISTS idx_sub_orders_order_id ON order_svc.sub_orders (order_id);
CREATE INDEX IF NOT EXISTS idx_sub_orders_seller_status ON order_svc.sub_orders (seller_id, fulfillment_status);
CREATE INDEX IF NOT EXISTS idx_order_items_sub_order_id ON order_svc.order_items (sub_order_id);
