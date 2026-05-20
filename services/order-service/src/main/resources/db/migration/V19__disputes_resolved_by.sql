-- Audit-log integrity: pt17 audit found that AdminDisputeController.resolve()
-- never recorded which admin performed the action. The dispute table could
-- answer "what was decided" but not "who decided it". Add resolved_by, stamped
-- from the JWT principal at resolve time.
--
-- Nullable on purpose — disputes still in OPEN state have no resolver, and
-- pre-existing rows stay null (we don't have a historical record to backfill).
ALTER TABLE order_svc.disputes
    ADD COLUMN IF NOT EXISTS resolved_by VARCHAR(255);
