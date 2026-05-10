package com.vnshop.reviewservice.application;

import com.vnshop.reviewservice.domain.Review;
import com.vnshop.reviewservice.domain.ReviewStatus;
import com.vnshop.reviewservice.domain.port.out.ReviewRepositoryPort;

import java.util.List;
import java.util.Objects;

public class ModerateReviewUseCase {
    private final ReviewRepositoryPort reviewRepositoryPort;

    public ModerateReviewUseCase(ReviewRepositoryPort reviewRepositoryPort) {
        this.reviewRepositoryPort = Objects.requireNonNull(reviewRepositoryPort, "reviewRepositoryPort is required");
    }

    public List<Review> pending() {
        return reviewRepositoryPort.findByStatus(ReviewStatus.PENDING);
    }

    public Review approve(String reviewId) {
        return reviewRepositoryPort.moderate(reviewId, ReviewStatus.APPROVED);
    }

    public Review reject(String reviewId) {
        return reviewRepositoryPort.moderate(reviewId, ReviewStatus.REJECTED);
    }
}
