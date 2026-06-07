-- Track 5.3: GDPR Compliance Audit
-- Track per-service deletion outcomes so partial failures are visible and retryable.
CREATE TABLE IF NOT EXISTS user_svc.gdpr_deletion_status (
    id              BIGSERIAL PRIMARY KEY,
    request_id      VARCHAR(36)  NOT NULL,
    user_id         VARCHAR(255) NOT NULL,
    service_name    VARCHAR(100) NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    error_message   TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (request_id, service_name)
);

CREATE INDEX IF NOT EXISTS idx_gdpr_deletion_status_request_id
    ON user_svc.gdpr_deletion_status (request_id);

CREATE INDEX IF NOT EXISTS idx_gdpr_deletion_status_user_id
    ON user_svc.gdpr_deletion_status (user_id);
