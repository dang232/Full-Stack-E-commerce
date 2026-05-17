-- Stock reservations + projected stock levels for the gRPC Reserve/Release path.
--
-- Reserve handler decrements stock_levels.available_quantity in a single
-- atomic UPDATE, gated on `available_quantity >= :qty`. If the row does not
-- exist (no projection yet for that product), the handler logs a warning and
-- proceeds without decrementing — this keeps brand-new products orderable
-- while we still don't event-source projection from product-service.
--
-- Release walks reservations by order_id, refunds the quantity to
-- stock_levels, and marks each reservation RELEASED. Idempotent: a second
-- Release for the same order_id is a no-op.

CREATE TABLE inventory_svc.stock_levels (
    product_id          TEXT        PRIMARY KEY,
    available_quantity  INTEGER     NOT NULL CHECK (available_quantity >= 0),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE inventory_svc.stock_reservations (
    reservation_id  UUID        PRIMARY KEY,
    order_id        TEXT        NOT NULL,
    product_id      TEXT        NOT NULL,
    variant         TEXT,
    quantity        INTEGER     NOT NULL CHECK (quantity > 0),
    status          TEXT        NOT NULL CHECK (status IN ('RESERVED', 'RELEASED')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    released_at     TIMESTAMPTZ NULL
);

CREATE INDEX idx_stock_reservations_order_id
    ON inventory_svc.stock_reservations (order_id);
