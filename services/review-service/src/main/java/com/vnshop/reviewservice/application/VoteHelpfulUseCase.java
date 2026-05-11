package com.vnshop.reviewservice.application;

import com.vnshop.reviewservice.domain.Review;
import com.vnshop.reviewservice.domain.port.out.ReviewRepositoryPort;

import java.util.Objects;
import java.util.UUID;

public class VoteHelpfulUseCase {
    private final ReviewRepositoryPort reviewRepositoryPort;

    public VoteHelpfulUseCase(ReviewRepositoryPort reviewRepositoryPort) {
        this.reviewRepositoryPort = Objects.requireNonNull(reviewRepositoryPort, "reviewRepositoryPort is required");
    }

    public Review vote(UUID reviewId) {
        Review review = reviewRepositoryPort.findReviewById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("review not found: " + reviewId));
        return reviewRepositoryPort.save(review.withHelpfulVote());
    }
}
