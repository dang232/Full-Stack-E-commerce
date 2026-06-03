CREATE TABLE audit_log (
    id              BIGSERIAL PRIMARY KEY,
    timestamp       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    user_id         VARCHAR(255),
    user_role       VARCHAR(50),
    action          VARCHAR(100) NOT NULL,
    resource_type   VARCHAR(100) NOT NULL,
    resource_id     VARCHAR(255),
    details         JSONB,
    ip_address      VARCHAR(45),
    correlation_id  VARCHAR(100),
    service_name    VARCHAR(50) NOT NULL DEFAULT 'order-service'
);

-- Append-only: no UPDATE or DELETE grants in production
CREATE INDEX idx_audit_log_timestamp ON audit_log (timestamp DESC);
CREATE INDEX idx_audit_log_user_id ON audit_log (user_id, timestamp DESC);
CREATE INDEX idx_audit_log_resource ON audit_log (resource_type, resource_id);
CREATE INDEX idx_audit_log_action ON audit_log (action, timestamp DESC);

COMMENT ON TABLE audit_log IS 'Immutable audit trail — append only. Do not grant UPDATE/DELETE in production.';
