-- Triage index for dead-lettered outbox events. Operators querying for DEAD events
-- (manual reprocess, alerting, dashboards) should not full-scan outbox_events as
-- the table grows.
CREATE INDEX IF NOT EXISTS idx_outbox_events_dead
    ON order_svc.outbox_events (created_at DESC)
    WHERE status = 'DEAD';
