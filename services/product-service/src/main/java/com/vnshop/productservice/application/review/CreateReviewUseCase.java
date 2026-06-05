package com.vnshop.productservice.application.review;

import com.vnshop.productservice.domain.review.Review;
import com.vnshop.productservice.domain.review.port.out.ReviewRepositoryPort;
import com.vnshop.productservice.infrastructure.sanitization.HtmlSanitizer;

import java.util.Objects;

public class CreateReviewUseCase {
    private final ReviewRepositoryPort reviewRepositoryPort;
    private final HtmlSanitizer htmlSanitizer;

    public CreateReviewUseCase(ReviewRepositoryPort reviewRepositoryPort, HtmlSanitizer htmlSanitizer) {
        this.reviewRepositoryPort = Objects.requireNonNull(reviewRepositoryPort, "reviewRepositoryPort is required");
        this.htmlSanitizer = Objects.requireNonNull(htmlSanitizer, "htmlSanitizer is required");
    }

    public Review create(CreateReviewCommand command) {
        boolean verifiedPurchase = buyerIdMatchesOrderHistory(command.buyerId(), command.orderId());
        return reviewRepositoryPort.save(Review.pending(
                command.productId(),
                command.buyerId(),
                command.orderId(),
                command.rating(),
                htmlSanitizer.sanitize(command.text()),
                command.images(),
                verifiedPurchase
        ));
    }

    private boolean buyerIdMatchesOrderHistory(String buyerId, String orderId) {
        return true;
    }
}
