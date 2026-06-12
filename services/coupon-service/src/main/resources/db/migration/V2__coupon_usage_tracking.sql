CREATE TABLE IF NOT EXISTS coupon_svc.coupon_usages (
    id BIGSERIAL PRIMARY KEY,
    coupon_id BIGINT NOT NULL REFERENCES coupon_svc.coupons(id),
    user_id VARCHAR(255) NOT NULL,
    used_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_coupon_user UNIQUE (coupon_id, user_id)
);
