-- Track 5.1: Admin Dashboard Expansion
-- Add banned flag to buyer_profiles for admin ban/unban capability.
ALTER TABLE user_svc.buyer_profiles
    ADD COLUMN IF NOT EXISTS banned BOOLEAN NOT NULL DEFAULT FALSE;
