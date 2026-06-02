CREATE TABLE product_svc.review_helpful_voters (
    review_id UUID NOT NULL,
    voter_id VARCHAR(64) NOT NULL,
    PRIMARY KEY (review_id, voter_id),
    CONSTRAINT fk_review_helpful_voters_review FOREIGN KEY (review_id) REFERENCES product_svc.reviews (review_id)
);
