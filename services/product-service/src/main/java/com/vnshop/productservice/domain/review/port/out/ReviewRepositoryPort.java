package com.vnshop.productservice.domain.review.port.out;

import com.vnshop.productservice.domain.review.ProductQuestion;
import com.vnshop.productservice.domain.review.Review;
import com.vnshop.productservice.domain.review.ReviewStatus;

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
