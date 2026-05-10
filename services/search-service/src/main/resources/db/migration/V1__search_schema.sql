CREATE SCHEMA IF NOT EXISTS search_svc;

CREATE TABLE IF NOT EXISTS search_svc.product_read_models (
    product_id VARCHAR(255) PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(2000),
    category_id VARCHAR(255),
    brand VARCHAR(255),
    status VARCHAR(32) NOT NULL,
    min_price NUMERIC(19, 0),
    max_price NUMERIC(19, 0),
    variant_count INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_product_read_models_category_id ON search_svc.product_read_models (category_id);
CREATE INDEX IF NOT EXISTS idx_product_read_models_brand ON search_svc.product_read_models (brand);
CREATE INDEX IF NOT EXISTS idx_product_read_models_name_lower ON search_svc.product_read_models (LOWER(name));
