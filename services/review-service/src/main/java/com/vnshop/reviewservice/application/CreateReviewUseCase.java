package com.vnshop.reviewservice.application;

import com.vnshop.reviewservice.domain.Review;
import com.vnshop.reviewservice.domain.port.out.ReviewRepositoryPort;

import java.util.List;
import java.util.Objects;

public class CreateReviewUseCase {
    private final ReviewRepositoryPort reviewRepositoryPort;

    public CreateReviewUseCase(ReviewRepositoryPort reviewRepositoryPort) {
        this.reviewRepositoryPort = Objects.requireNonNull(reviewRepositoryPort, "reviewRepositoryPort is required");
    }

    public Review create(String productId, String buyerId, String orderId, int rating, String text, List<String> images) {
        boolean verifiedPurchase = buyerIdMatchesOrderHistory(buyerId, orderId);
        return reviewRepositoryPort.save(Review.pending(productId, buyerId, orderId, rating, text, images, verifiedPurchase));
    }

    private boolean buyerIdMatchesOrderHistory(String buyerId, String orderId) {
        return true;
    }
}
