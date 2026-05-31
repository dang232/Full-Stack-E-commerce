CREATE SCHEMA IF NOT EXISTS product_svc;

CREATE TABLE product_svc.products (
    id UUID PRIMARY KEY,
    seller_id VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(2000),
    category_id VARCHAR(255),
    brand VARCHAR(255),
    status VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE product_svc.product_variants (
    product_id UUID NOT NULL,
    sku VARCHAR(255) NOT NULL,
    name VARCHAR(255),
    price_amount DECIMAL(19, 2) NOT NULL,
    price_currency VARCHAR(255) NOT NULL,
    image_url VARCHAR(255),
    CONSTRAINT fk_product_variants_product FOREIGN KEY (product_id) REFERENCES product_svc.products (id)
);

CREATE TABLE product_svc.product_images (
    product_id UUID NOT NULL,
    url VARCHAR(255) NOT NULL,
    alt VARCHAR(255),
    sort_order INTEGER NOT NULL,
    CONSTRAINT fk_product_images_product FOREIGN KEY (product_id) REFERENCES product_svc.products (id)
);

CREATE TABLE product_svc.object_metadata (
    object_key VARCHAR(1024) PRIMARY KEY,
    storage_class VARCHAR(255) NOT NULL,
    content_type VARCHAR(255) NOT NULL,
    content_length BIGINT NOT NULL,
    sha256_hex VARCHAR(64) NOT NULL,
    quarantine_state VARCHAR(255) NOT NULL,
    image_width INTEGER,
    image_height INTEGER,
    owner_type VARCHAR(255) NOT NULL,
    owner_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE product_svc.reviews (
    review_id UUID PRIMARY KEY,
    product_id VARCHAR(255) NOT NULL,
    buyer_id VARCHAR(255) NOT NULL,
    order_id VARCHAR(255) NOT NULL,
    rating INTEGER NOT NULL,
    text VARCHAR(1000) NOT NULL,
    verified_purchase BOOLEAN NOT NULL,
    helpful_votes INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE product_svc.review_images (
    review_id UUID NOT NULL,
    image_order INTEGER NOT NULL,
    image_url VARCHAR(1024) NOT NULL,
    CONSTRAINT fk_review_images_review FOREIGN KEY (review_id) REFERENCES product_svc.reviews (review_id)
);

CREATE TABLE product_svc.product_questions (
    question_id UUID PRIMARY KEY,
    product_id VARCHAR(255) NOT NULL,
    buyer_id VARCHAR(255) NOT NULL,
    question VARCHAR(1000) NOT NULL,
    answer VARCHAR(1000),
    answered_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
