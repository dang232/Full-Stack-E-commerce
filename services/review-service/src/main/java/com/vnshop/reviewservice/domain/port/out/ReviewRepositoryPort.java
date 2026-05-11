package com.vnshop.reviewservice.domain.port.out;

import com.vnshop.reviewservice.domain.ProductQuestion;
import com.vnshop.reviewservice.domain.Review;
import com.vnshop.reviewservice.domain.ReviewStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewRepositoryPort {
    Review save(Review review);

    List<Review> findByProductId(String productId);

    List<Review> findByBuyerId(String buyerId);

    List<Review> findByStatus(ReviewStatus status);

    Optional<Review> findReviewById(UUID reviewId);

    Review moderate(UUID reviewId, ReviewStatus status);

    ProductQuestion saveQuestion(ProductQuestion question);

    List<ProductQuestion> findQuestionsByProductId(String productId);

    Optional<ProductQuestion> findQuestionById(UUID questionId);
}
