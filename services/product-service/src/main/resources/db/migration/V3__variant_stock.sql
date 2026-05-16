-- Phase 3C: fold stock into the product detail response.
--
-- Inventory-service has no stock table yet (V1 migration there is empty), so
-- product-service owns the per-variant stock the seller declared at create time
-- and the read API sums it. When real reservation tracking lands in
-- inventory-service, it can take over without changing the response shape.
ALTER TABLE product_svc.product_variants
    ADD COLUMN stock_quantity INTEGER NOT NULL DEFAULT 0;
