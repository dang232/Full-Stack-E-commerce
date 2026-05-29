CREATE TABLE order_svc.seller_commission_tier (
    seller_id VARCHAR(255) PRIMARY KEY,
    tier VARCHAR(20) NOT NULL DEFAULT 'STANDARD'
);

ALTER TABLE order_svc.sub_orders
    ADD COLUMN commission_tier VARCHAR(20) NOT NULL DEFAULT 'STANDARD';
