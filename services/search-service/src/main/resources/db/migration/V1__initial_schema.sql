CREATE SCHEMA IF NOT EXISTS search_svc;

CREATE TABLE search_svc.product_read_models (
    product_id VARCHAR(255) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(2000),
    category_id VARCHAR(255),
    brand VARCHAR(255),
    status VARCHAR(255) NOT NULL,
    min_price DECIMAL(19, 2),
    max_price DECIMAL(19, 2),
    variant_count INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL
);
