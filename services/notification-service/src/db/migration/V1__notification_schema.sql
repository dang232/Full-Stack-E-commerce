CREATE SCHEMA IF NOT EXISTS notification_svc;

CREATE TABLE IF NOT EXISTS notification_svc.notifications (
  id UUID PRIMARY KEY,
  user_id VARCHAR(64) NOT NULL,
  type VARCHAR(64) NOT NULL CHECK (type IN ('ORDER_CREATED', 'ORDER_CANCELLED', 'ORDER_SHIPPED', 'PAYMENT_COMPLETED')),
  title VARCHAR(255) NOT NULL,
  body TEXT NOT NULL,
  data JSONB NOT NULL DEFAULT '{}'::jsonb,
  channels TEXT[] NOT NULL,
  status VARCHAR(32) NOT NULL CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_notifications_user_created_at
  ON notification_svc.notifications (user_id, created_at DESC);
