-- Active flash-sale campaigns visible to the storefront. Each row is one
-- product on sale within a window, with the original/sale price and the
-- starting stock allocated to the campaign. Live remaining stock lives in
-- Redis (flash:stock:<productId>) and is joined at read-time by the
-- GET /flash-sale/active endpoint.
--
-- `active` lets ops disable a campaign without rewriting the window.

CREATE TABLE inventory_svc.flash_sale_campaigns (
    id              UUID            PRIMARY KEY,
    product_id      TEXT            NOT NULL,
    original_price  NUMERIC(18, 2)  NOT NULL CHECK (original_price >= 0),
    sale_price      NUMERIC(18, 2)  NOT NULL CHECK (sale_price >= 0),
    stock_total     INTEGER         NOT NULL CHECK (stock_total >= 0),
    starts_at       TIMESTAMPTZ     NOT NULL,
    ends_at         TIMESTAMPTZ     NOT NULL,
    active          BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CHECK (ends_at > starts_at)
);

-- Common query: list everything currently running, ordered by ends_at asc.
CREATE INDEX idx_flash_sale_campaigns_window
    ON inventory_svc.flash_sale_campaigns (active, starts_at, ends_at);
