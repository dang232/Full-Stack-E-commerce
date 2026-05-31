-- Backfill order_summary projection from existing orders.
-- Safe to re-run: ON CONFLICT (order_id) DO UPDATE keeps the projection in sync
-- with whatever the OrderProjectionListener has already written for new orders.
INSERT INTO order_svc.order_summary (
    order_id,
    buyer_id,
    seller_id,
    status,
    total_amount,
    item_count,
    created_at,
    updated_at
)
SELECT
    o.id::text,
    o.buyer_id,
    (SELECT so.seller_id FROM order_svc.sub_orders so WHERE so.order_id = o.id ORDER BY so.id LIMIT 1),
    o.payment_status,
    o.final_amount,
    COALESCE((
        SELECT SUM(oi.quantity)::int
        FROM order_svc.sub_orders so
        JOIN order_svc.order_items oi ON oi.sub_order_id = so.id
        WHERE so.order_id = o.id
    ), 0),
    COALESCE(o.created_at, NOW()),
    COALESCE(o.created_at, NOW())
FROM order_svc.orders o
ON CONFLICT (order_id) DO UPDATE SET
    buyer_id     = EXCLUDED.buyer_id,
    seller_id    = COALESCE(EXCLUDED.seller_id, order_svc.order_summary.seller_id),
    status       = EXCLUDED.status,
    total_amount = EXCLUDED.total_amount,
    item_count   = EXCLUDED.item_count,
    updated_at   = NOW();

CREATE INDEX IF NOT EXISTS idx_order_summary_buyer_created
    ON order_svc.order_summary (buyer_id, created_at DESC);
