-- V9: cross-currency audit columns on payments. Stripe and PayPal charge in
-- USD; order totals stay in VND. Storing the converted amount + the rate that
-- produced it lets a dispute three months from now be resolved in seconds
-- ("we charged $4.12 because frankfurter.app said 1 USD = 24,272 VND that
-- morning") rather than as a research project. Also covers the case where
-- Frankfurter is unreachable and we fell back to FX_FALLBACK_RATE — the row
-- shows that too.
--
-- Idempotent — ADD COLUMN IF NOT EXISTS so re-runs are safe.

ALTER TABLE payment_svc.payments
    ADD COLUMN IF NOT EXISTS external_amount NUMERIC(19,2),
    ADD COLUMN IF NOT EXISTS external_currency VARCHAR(3),
    ADD COLUMN IF NOT EXISTS fx_rate NUMERIC(19,8),
    ADD COLUMN IF NOT EXISTS fx_rate_at TIMESTAMP WITH TIME ZONE;
