package com.vnshop.reviewservice.infrastructure.persistence;

import com.vnshop.reviewservice.domain.ProductQuestion;
import com.vnshop.reviewservice.domain.Review;
import com.vnshop.reviewservice.domain.ReviewStatus;
import com.vnshop.reviewservice.domain.port.out.ReviewRepositoryPort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ReviewJpaRepository implements ReviewRepositoryPort {
    private final ReviewJpaSpringDataRepository reviewRepository;
    private final QuestionJpaSpringDataRepository questionRepository;

    public ReviewJpaRepository(ReviewJpaSpringDataRepository reviewRepository,
            QuestionJpaSpringDataRepository questionRepository) {
        this.reviewRepository = reviewRepository;
        this.questionRepository = questionRepository;
    }

    @Override
    public Review save(Review review) {
        return reviewRepository.save(ReviewJpaEntity.fromDomain(review)).toDomain();
    }

    @Override
    public List<Review> findByProductId(String productId) {
        return reviewRepository.findByProductId(productId).stream().map(ReviewJpaEntity::toDomain).toList();
    }

    @Override
    public List<Review> findByBuyerId(String buyerId) {
        return reviewRepository.findByBuyerId(buyerId).stream().map(ReviewJpaEntity::toDomain).toList();
    }

    @Override
    public List<Review> findByStatus(ReviewStatus status) {
        return reviewRepository.findByStatus(status).stream().map(ReviewJpaEntity::toDomain).toList();
    }

    @Override
    public Optional<Review> findReviewById(UUID reviewId) {
        return reviewRepository.findById(reviewId).map(ReviewJpaEntity::toDomain);
    }

    @Override
    public Review moderate(UUID reviewId, ReviewStatus status) {
        Review review = findReviewById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("review not found: " + reviewId));
        return save(review.withStatus(status));
    }

    @Override
    public ProductQuestion saveQuestion(ProductQuestion question) {
        return questionRepository.save(QuestionJpaEntity.fromDomain(question)).toDomain();
    }

    @Override
    public List<ProductQuestion> findQuestionsByProductId(String productId) {
        return questionRepository.findByProductId(productId).stream().map(QuestionJpaEntity::toDomain).toList();
    }

    @Override
    public Optional<ProductQuestion> findQuestionById(UUID questionId) {
        return questionRepository.findById(questionId).map(QuestionJpaEntity::toDomain);
    }
}

interface ReviewJpaSpringDataRepository extends JpaRepository<ReviewJpaEntity, UUID> {
    List<ReviewJpaEntity> findByProductId(String productId);

    List<ReviewJpaEntity> findByBuyerId(String buyerId);

    List<ReviewJpaEntity> findByStatus(ReviewStatus status);
}

interface QuestionJpaSpringDataRepository extends JpaRepository<QuestionJpaEntity, UUID> {
    List<QuestionJpaEntity> findByProductId(String productId);
}
