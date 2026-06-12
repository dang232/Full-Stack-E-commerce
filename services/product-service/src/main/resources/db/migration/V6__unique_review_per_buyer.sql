ALTER TABLE product_svc.reviews
    ADD CONSTRAINT uq_review_product_buyer UNIQUE (product_id, buyer_id);
