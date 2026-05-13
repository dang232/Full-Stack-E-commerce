CREATE TABLE IF NOT EXISTS order_svc.saga_state (
    saga_id VARCHAR(36) PRIMARY KEY,
    order_id VARCHAR(36) NOT NULL,
    current_step VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);