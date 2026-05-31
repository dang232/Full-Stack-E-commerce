CREATE SCHEMA IF NOT EXISTS messaging_svc;

-- Direct buyer-seller threads. Optionally scoped to a product so the same
-- buyer/seller pair can spin up separate threads per product (Shopee/Lazada
-- pattern). product_id is NULL for "general shop chat" threads.
CREATE TABLE IF NOT EXISTS messaging_svc.threads (
  id UUID PRIMARY KEY,
  buyer_id VARCHAR(64) NOT NULL,
  seller_id VARCHAR(64) NOT NULL,
  product_id VARCHAR(64) NULL,
  last_message_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  buyer_last_read_at TIMESTAMPTZ NULL,
  seller_last_read_at TIMESTAMPTZ NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Idempotency for "open chat from product page" — POST /threads with the same
-- (recipient, product) returns the existing row instead of creating a duplicate.
-- COALESCE so the partial-uniqueness applies to NULL product_id too.
CREATE UNIQUE INDEX IF NOT EXISTS ux_threads_buyer_seller_product
  ON messaging_svc.threads (buyer_id, seller_id, COALESCE(product_id, ''));

CREATE INDEX IF NOT EXISTS idx_threads_buyer_last_msg
  ON messaging_svc.threads (buyer_id, last_message_at DESC);
CREATE INDEX IF NOT EXISTS idx_threads_seller_last_msg
  ON messaging_svc.threads (seller_id, last_message_at DESC);

CREATE TABLE IF NOT EXISTS messaging_svc.messages (
  id UUID PRIMARY KEY,
  thread_id UUID NOT NULL REFERENCES messaging_svc.threads(id) ON DELETE CASCADE,
  sender_id VARCHAR(64) NOT NULL,
  body TEXT NOT NULL,
  sent_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Drives both thread-detail pagination ("messages newest first") and the
-- last-message preview the thread list shows.
CREATE INDEX IF NOT EXISTS idx_messages_thread_sent_at
  ON messaging_svc.messages (thread_id, sent_at DESC);
