-- Wishlist storage for buyers. One row per (keycloak_id, product_id) — keeps it
-- a pure many-to-many. Cap is enforced at the application layer (max 200 items
-- per user) rather than via a constraint, so soft-violation diagnostics stay
-- readable.
CREATE TABLE IF NOT EXISTS user_svc.wishlist_items (
    keycloak_id VARCHAR(255) NOT NULL,
    product_id  VARCHAR(255) NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (keycloak_id, product_id)
);

CREATE INDEX IF NOT EXISTS idx_wishlist_items_keycloak_id_created_at
    ON user_svc.wishlist_items (keycloak_id, created_at DESC);
