CREATE SCHEMA IF NOT EXISTS review_svc;

CREATE TABLE IF NOT EXISTS review_svc.reviews (
    review_id uuid PRIMARY KEY,
    product_id VARCHAR(255) NOT NULL,
    buyer_id VARCHAR(255) NOT NULL,
    order_id VARCHAR(255) NOT NULL,
    rating INTEGER NOT NULL CHECK (rating BETWEEN 1 AND 5),
    text VARCHAR(1000) NOT NULL,
    verified_purchase BOOLEAN NOT NULL,
    helpful_votes INTEGER NOT NULL DEFAULT 0 CHECK (helpful_votes >= 0),
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS review_svc.review_images (
    review_id uuid NOT NULL REFERENCES review_svc.reviews (review_id) ON DELETE CASCADE,
    image_order INTEGER NOT NULL,
    image_url VARCHAR(1024) NOT NULL,
    PRIMARY KEY (review_id, image_order)
);

CREATE TABLE IF NOT EXISTS review_svc.product_questions (
    question_id uuid PRIMARY KEY,
    product_id VARCHAR(255) NOT NULL,
    buyer_id VARCHAR(255) NOT NULL,
    question VARCHAR(1000) NOT NULL,
    answer VARCHAR(1000),
    answered_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_reviews_product_id ON review_svc.reviews (product_id);
CREATE INDEX IF NOT EXISTS idx_reviews_buyer_id ON review_svc.reviews (buyer_id);
CREATE INDEX IF NOT EXISTS idx_reviews_status ON review_svc.reviews (status);
CREATE INDEX IF NOT EXISTS idx_product_questions_product_id ON review_svc.product_questions (product_id);
