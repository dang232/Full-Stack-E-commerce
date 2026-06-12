CREATE TABLE IF NOT EXISTS processed_order_event (
    event_id      VARCHAR(255) PRIMARY KEY,
    processed_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
