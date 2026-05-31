ALTER TABLE order_svc.orders
    ADD COLUMN external_amount DECIMAL(19, 4),
    ADD COLUMN external_currency VARCHAR(3),
    ADD COLUMN fx_rate DECIMAL(12, 6),
    ADD COLUMN fx_rate_at TIMESTAMP;
