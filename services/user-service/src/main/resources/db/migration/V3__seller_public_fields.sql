ALTER TABLE user_svc.seller_profiles
    ADD COLUMN description TEXT,
    ADD COLUMN logo_url    VARCHAR(500),
    ADD COLUMN banner_url  VARCHAR(500);

CREATE INDEX idx_seller_profiles_approved_created
    ON user_svc.seller_profiles (approved, created_at DESC)
    WHERE approved = true;
