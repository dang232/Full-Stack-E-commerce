package com.vnshop.productservice.application.review;

import com.vnshop.productservice.domain.review.SellerReviewSummary;
import com.vnshop.productservice.domain.review.port.out.ReviewRepositoryPort;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class SellerReviewSummaryUseCase {
    private final ReviewRepositoryPort reviewRepositoryPort;

    public SellerReviewSummaryUseCase(ReviewRepositoryPort reviewRepositoryPort) {
        this.reviewRepositoryPort = Objects.requireNonNull(reviewRepositoryPort, "reviewRepositoryPort is required");
    }

    public SellerReviewSummary getSummary(String sellerId) {
        return reviewRepositoryPort.getSellerReviewSummary(sellerId);
    }

    public Map<String, SellerReviewSummary> getSummaries(Set<String> sellerIds) {
        validateSellerIds(sellerIds);
        return reviewRepositoryPort.getSellerReviewSummaries(sellerIds);
    }

    private void validateSellerIds(Set<String> sellerIds) {
        if (sellerIds == null) {
            throw new IllegalArgumentException("sellerIds must not be null");
        }
        if (sellerIds.isEmpty()) {
            throw new IllegalArgumentException("sellerIds must not be empty");
        }
        if (sellerIds.size() > 100) {
            throw new IllegalArgumentException("sellerIds must not exceed 100 entries");
        }
        for (String id : sellerIds) {
            if (id == null) {
                throw new IllegalArgumentException("sellerIds must not contain null entries");
            }
        }
    }
}
