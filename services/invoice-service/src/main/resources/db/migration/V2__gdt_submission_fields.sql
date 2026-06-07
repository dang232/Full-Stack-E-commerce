-- Track 7.3: GDT API Integration
-- Adds GDT submission tracking columns to invoices table.
-- All XML payloads are retained permanently (10-year regulatory obligation).

ALTER TABLE invoices
    ADD COLUMN IF NOT EXISTS gdt_verification_code VARCHAR(255),
    ADD COLUMN IF NOT EXISTS rejection_reason      TEXT,
    ADD COLUMN IF NOT EXISTS submitted_at          TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS gdt_response_json     TEXT;
