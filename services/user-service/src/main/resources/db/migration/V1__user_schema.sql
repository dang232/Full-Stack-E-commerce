CREATE SCHEMA IF NOT EXISTS user_svc;

CREATE TABLE IF NOT EXISTS user_svc.buyer_profiles (
    id BIGSERIAL PRIMARY KEY,
    keycloak_id VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255),
    phone VARCHAR(32),
    avatar_url VARCHAR(1024)
);

CREATE TABLE IF NOT EXISTS user_svc.addresses (
    id BIGSERIAL PRIMARY KEY,
    buyer_profile_id BIGINT NOT NULL,
    street VARCHAR(255) NOT NULL,
    ward VARCHAR(255),
    district VARCHAR(255) NOT NULL,
    city VARCHAR(255) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_addresses_buyer_profiles
        FOREIGN KEY (buyer_profile_id)
        REFERENCES user_svc.buyer_profiles (id)
        ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS user_svc.seller_profiles (
    id BIGSERIAL PRIMARY KEY,
    keycloak_id VARCHAR(255) NOT NULL UNIQUE,
    shop_name VARCHAR(255) NOT NULL,
    bank_name VARCHAR(255) NOT NULL,
    bank_account VARCHAR(255) NOT NULL,
    pickup_address_street VARCHAR(255),
    pickup_address_ward VARCHAR(255),
    pickup_address_district VARCHAR(255),
    pickup_address_city VARCHAR(255),
    pickup_address_default BOOLEAN NOT NULL DEFAULT FALSE,
    approved BOOLEAN NOT NULL DEFAULT FALSE,
    tier VARCHAR(32) NOT NULL,
    vacation_mode BOOLEAN NOT NULL DEFAULT FALSE
);
