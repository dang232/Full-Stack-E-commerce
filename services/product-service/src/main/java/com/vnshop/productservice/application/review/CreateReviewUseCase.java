package com.vnshop.productservice.application.review;

import com.vnshop.productservice.domain.review.Review;
import com.vnshop.productservice.domain.review.port.out.ReviewRepositoryPort;

import java.util.List;
import java.util.Objects;

public class CreateReviewUseCase {
    private final ReviewRepositoryPort reviewRepositoryPort;

    public CreateReviewUseCase(ReviewRepositoryPort reviewRepositoryPort) {
        this.reviewRepositoryPort = Objects.requireNonNull(reviewRepositoryPort, "reviewRepositoryPort is required");
    }

    public Review create(CreateReviewCommand command) {
        boolean verifiedPurchase = buyerIdMatchesOrderHistory(command.buyerId(), command.orderId());
        return reviewRepositoryPort.save(Review.pending(command.productId(), command.buyerId(), command.orderId(), command.rating(), command.text(), command.images(), verifiedPurchase));
    }

    private boolean buyerIdMatchesOrderHistory(String buyerId, String orderId) {
        return true;
    }
}
