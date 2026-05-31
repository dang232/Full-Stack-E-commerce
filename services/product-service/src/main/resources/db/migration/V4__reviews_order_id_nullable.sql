-- Reviews.order_id is optional per the controller contract — buyers can
-- post reviews from a product page without selecting which past order to
-- attribute the review to. The original V1 schema declared NOT NULL, which
-- contradicted the API surface and short-circuited every review-from-
-- product-page submit with a 23502 constraint violation. Caught by AC-4.2
-- of the BA-grade journey suite.
ALTER TABLE product_svc.reviews ALTER COLUMN order_id DROP NOT NULL;
