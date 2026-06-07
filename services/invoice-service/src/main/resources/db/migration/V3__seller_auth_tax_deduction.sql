-- Track 7.4: Seller Authorization Management
-- Adds tax_deduction_percent to seller_authorizations and tax_deduction_amount to invoices.

ALTER TABLE seller_authorizations
    ADD COLUMN IF NOT EXISTS tax_deduction_percent INT NOT NULL DEFAULT 10;

ALTER TABLE invoices
    ADD COLUMN IF NOT EXISTS tax_deduction_amount NUMERIC(19, 4);
