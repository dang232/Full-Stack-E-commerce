package com.vnshop.productservice.application.review;

import com.vnshop.productservice.domain.review.Review;
import com.vnshop.productservice.domain.review.ReviewStatus;
import com.vnshop.productservice.domain.review.port.out.ReviewRepositoryPort;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class ModerateReviewUseCase {
    private final ReviewRepositoryPort reviewRepositoryPort;

    public ModerateReviewUseCase(ReviewRepositoryPort reviewRepositoryPort) {
        this.reviewRepositoryPort = Objects.requireNonNull(reviewRepositoryPort, "reviewRepositoryPort is required");
    }

    public List<Review> pending() {
        return reviewRepositoryPort.findByStatus(ReviewStatus.PENDING);
    }

    public Review approve(UUID reviewId) {
        return reviewRepositoryPort.moderate(reviewId, ReviewStatus.APPROVED);
    }

    public Review reject(UUID reviewId) {
        return reviewRepositoryPort.moderate(reviewId, ReviewStatus.REJECTED);
    }
}
