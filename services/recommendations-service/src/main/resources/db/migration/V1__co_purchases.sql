-- Co-purchase aggregation table populated by the order.created Kafka listener.
-- Symmetric: every (A,B) pair seen in an order is recorded as both (A,B) and
-- (B,A) so reads against `product_a = ?` always return co-occurring items
-- without needing UNION queries at read time.
CREATE TABLE IF NOT EXISTS co_purchases (
    product_a    VARCHAR(64) NOT NULL,
    product_b    VARCHAR(64) NOT NULL,
    co_count     BIGINT      NOT NULL DEFAULT 0,
    last_seen_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_co_purchases PRIMARY KEY (product_a, product_b)
);

-- Supports the "top N co-purchased with X" query without a sort.
CREATE INDEX IF NOT EXISTS idx_co_purchases_a_count
    ON co_purchases (product_a, co_count DESC);

-- Idempotency guard for the Kafka listener: an order_created replay must not
-- inflate the co-purchase counts. We record every order id we have aggregated.
CREATE TABLE IF NOT EXISTS processed_orders (
    order_id     VARCHAR(64) NOT NULL,
    processed_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_processed_orders PRIMARY KEY (order_id)
);
