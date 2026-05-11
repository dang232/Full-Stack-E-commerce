CREATE SCHEMA IF NOT EXISTS product_svc;

CREATE TABLE IF NOT EXISTS product_svc.products (
    id uuid PRIMARY KEY,
    seller_id VARCHAR(255) NOT NULL,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(2000),
    category_id VARCHAR(255),
    brand VARCHAR(255),
    status VARCHAR(32) NOT NULL
);

CREATE TABLE IF NOT EXISTS product_svc.product_variants (
    product_id uuid NOT NULL,
    sku VARCHAR(255) NOT NULL,
    name VARCHAR(255),
    price_amount NUMERIC(19, 0) NOT NULL,
    price_currency VARCHAR(8) NOT NULL,
    image_url VARCHAR(1024),
    CONSTRAINT fk_product_variants_products
        FOREIGN KEY (product_id)
        REFERENCES product_svc.products (id)
        ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS product_svc.product_images (
    product_id uuid NOT NULL,
    url VARCHAR(1024) NOT NULL,
    alt VARCHAR(255),
    sort_order INTEGER NOT NULL,
    CONSTRAINT fk_product_images_products
        FOREIGN KEY (product_id)
        REFERENCES product_svc.products (id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_products_seller_id ON product_svc.products (seller_id);
CREATE INDEX IF NOT EXISTS idx_products_category_id ON product_svc.products (category_id);
CREATE INDEX IF NOT EXISTS idx_products_name_lower ON product_svc.products (LOWER(name));
