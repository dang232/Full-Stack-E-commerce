-- Tax rate configuration table (Vietnam VAT per Decree 94/2023)
CREATE TABLE IF NOT EXISTS order_svc.tax_rates (
    id            BIGSERIAL PRIMARY KEY,
    category_code VARCHAR(64)     NOT NULL UNIQUE,
    rate          DECIMAL(5, 4)   NOT NULL,
    effective_from DATE           NOT NULL,
    effective_to   DATE
);

-- Seed standard and reduced VAT rates
INSERT INTO order_svc.tax_rates (category_code, rate, effective_from)
VALUES
    ('STANDARD', 0.10, '2024-01-01'),
    ('REDUCED',  0.08, '2024-01-01')
ON CONFLICT (category_code) DO NOTHING;

-- Add tax fields to order_items
ALTER TABLE order_svc.order_items
    ADD COLUMN IF NOT EXISTS tax_rate   DECIMAL(5, 4),
    ADD COLUMN IF NOT EXISTS tax_amount NUMERIC(19, 0);

-- Add tax total to orders
ALTER TABLE order_svc.orders
    ADD COLUMN IF NOT EXISTS tax_total NUMERIC(19, 0) NOT NULL DEFAULT 0;
