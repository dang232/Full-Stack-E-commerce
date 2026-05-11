package com.vnshop.reviewservice.infrastructure.web;

import com.vnshop.reviewservice.domain.Review;

import java.time.Instant;
import java.util.List;

public record ReviewResponse(String reviewId, String productId, String buyerId, String orderId, int rating,
        String text, List<String> images, boolean verifiedPurchase, int helpfulVotes, String status,
        Instant createdAt) {
    static ReviewResponse fromDomain(Review review) {
        return new ReviewResponse(review.reviewId().toString(), review.productId(), review.buyerId(), review.orderId(),
                review.rating(), review.text(), review.images(), review.verifiedPurchase(), review.helpfulVotes(),
                review.status().name(), review.createdAt());
    }
}
