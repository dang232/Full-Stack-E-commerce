-- Track 5.3: GDPR Compliance — export request tracking
CREATE TABLE IF NOT EXISTS user_svc.gdpr_export_requests (
    request_id      VARCHAR(36)  PRIMARY KEY,
    user_id         VARCHAR(255) NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    fragments       TEXT,
    missing_services TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    completed_at    TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_gdpr_export_requests_user_id
    ON user_svc.gdpr_export_requests (user_id);

CREATE INDEX IF NOT EXISTS idx_gdpr_export_requests_created_at
    ON user_svc.gdpr_export_requests (created_at);
