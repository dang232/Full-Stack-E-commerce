CREATE TABLE IF NOT EXISTS order_svc.coupons (
    id uuid NOT NULL PRIMARY KEY,
    code VARCHAR(255) NOT NULL UNIQUE,
    type VARCHAR(32) NOT NULL,
    value NUMERIC(19, 0) NOT NULL,
    max_discount_amount NUMERIC(19, 0),
    min_order_value_amount NUMERIC(19, 0) NOT NULL,
    total_usage_limit INTEGER NOT NULL,
    total_used INTEGER NOT NULL,
    per_user_limit INTEGER NOT NULL,
    valid_from TIMESTAMP NOT NULL,
    valid_until TIMESTAMP NOT NULL,
    active BOOLEAN NOT NULL,
    coupon_created_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT chk_coupons_total_usage_limit_positive CHECK (total_usage_limit > 0),
    CONSTRAINT chk_coupons_total_used_non_negative CHECK (total_used >= 0),
    CONSTRAINT chk_coupons_per_user_limit_positive CHECK (per_user_limit > 0)
);

CREATE TABLE IF NOT EXISTS order_svc.coupon_usages (
    id uuid NOT NULL PRIMARY KEY,
    coupon_id uuid NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    order_id uuid NOT NULL,
    active BOOLEAN NOT NULL,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT fk_coupon_usages_coupons
        FOREIGN KEY (coupon_id)
        REFERENCES order_svc.coupons (id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_coupons_active_validity ON order_svc.coupons (active, valid_from, valid_until);
CREATE INDEX IF NOT EXISTS idx_coupon_usages_coupon_user_active ON order_svc.coupon_usages (coupon_id, user_id, active);
CREATE INDEX IF NOT EXISTS idx_coupon_usages_order_user_active ON order_svc.coupon_usages (order_id, user_id, active);
