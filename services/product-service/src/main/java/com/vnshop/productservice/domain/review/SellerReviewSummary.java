package com.vnshop.productservice.domain.review;

/**
 * Aggregate of review statistics for all products owned by a seller.
 * ratingAvg is null when ratingCount is zero.
 */
public record SellerReviewSummary(Double ratingAvg, long ratingCount) {
}
