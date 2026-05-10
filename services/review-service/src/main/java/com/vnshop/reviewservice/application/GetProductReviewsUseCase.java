package com.vnshop.reviewservice.application;

import com.vnshop.reviewservice.domain.Review;
import com.vnshop.reviewservice.domain.port.out.ReviewRepositoryPort;

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
