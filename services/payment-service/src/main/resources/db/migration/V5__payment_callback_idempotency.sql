CREATE TABLE IF NOT EXISTS payment_svc.payment_callback_logs (
    callback_id VARCHAR(255) PRIMARY KEY,
    provider VARCHAR(32) NOT NULL,
    event_id VARCHAR(255),
    payload_hash VARCHAR(64) NOT NULL,
    signature_hash VARCHAR(64) NOT NULL,
    request_headers TEXT NOT NULL,
    request_body TEXT NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL,
    processing_status VARCHAR(32) NOT NULL,
    duplicate_replay BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_payment_callback_logs_provider_event ON payment_svc.payment_callback_logs (provider, event_id);
CREATE INDEX IF NOT EXISTS idx_payment_callback_logs_provider_payload_signature ON payment_svc.payment_callback_logs (provider, payload_hash, signature_hash);
CREATE INDEX IF NOT EXISTS idx_payment_callback_logs_received_at ON payment_svc.payment_callback_logs (received_at);

CREATE OR REPLACE FUNCTION payment_svc.prevent_payment_callback_logs_mutation()
RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'payment_callback_logs are immutable';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_prevent_payment_callback_logs_update ON payment_svc.payment_callback_logs;
CREATE TRIGGER trg_prevent_payment_callback_logs_update
    BEFORE UPDATE ON payment_svc.payment_callback_logs
    FOR EACH ROW EXECUTE FUNCTION payment_svc.prevent_payment_callback_logs_mutation();

DROP TRIGGER IF EXISTS trg_prevent_payment_callback_logs_delete ON payment_svc.payment_callback_logs;
CREATE TRIGGER trg_prevent_payment_callback_logs_delete
    BEFORE DELETE ON payment_svc.payment_callback_logs
    FOR EACH ROW EXECUTE FUNCTION payment_svc.prevent_payment_callback_logs_mutation();
