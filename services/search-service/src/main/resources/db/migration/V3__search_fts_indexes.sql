-- pg_trgm-based fuzzy LIKE indexes for the product read model.
--
-- The search query in ProductReadModelRepository uses
--   lower(name) like '%:q%' or lower(description) like '%:q%'
-- The leading wildcard means the existing btree idx_product_read_models_name_lower
-- cannot be used, so every search is a sequential scan. pg_trgm + GIN gives us
-- index-backed substring matching for both columns.

CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_product_read_models_name_trgm
    ON search_svc.product_read_models USING GIN (name gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_product_read_models_description_trgm
    ON search_svc.product_read_models USING GIN (description gin_trgm_ops);
