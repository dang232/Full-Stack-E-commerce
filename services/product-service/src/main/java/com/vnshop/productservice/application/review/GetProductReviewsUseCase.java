package com.vnshop.productservice.application.review;

import com.vnshop.productservice.domain.review.Review;
import com.vnshop.productservice.domain.review.port.out.ReviewRepositoryPort;

import java.util.List;
import java.util.Objects;

public class GetProductReviewsUseCase {
    private final ReviewRepositoryPort reviewRepositoryPort;

    public GetProductReviewsUseCase(ReviewRepositoryPort reviewRepositoryPort) {
        this.reviewRepositoryPort = Objects.requireNonNull(reviewRepositoryPort, "reviewRepositoryPort is required");
    }

    public List<Review> get(String productId) {
        return reviewRepositoryPort.findByProductId(productId);
    }
}
