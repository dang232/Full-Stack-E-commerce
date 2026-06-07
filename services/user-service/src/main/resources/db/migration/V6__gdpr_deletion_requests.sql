-- Track 5.3: GDPR Compliance — request-level deletion tracking
-- Complements V5 (per-service status rows) with a single aggregate request record.
CREATE TABLE IF NOT EXISTS user_svc.gdpr_deletion_requests (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          VARCHAR(255) NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    completed_at     TIMESTAMPTZ,
    failed_services  TEXT
);

CREATE INDEX IF NOT EXISTS idx_gdpr_deletion_requests_user_id
    ON user_svc.gdpr_deletion_requests (user_id);

CREATE INDEX IF NOT EXISTS idx_gdpr_deletion_requests_status
    ON user_svc.gdpr_deletion_requests (status);
