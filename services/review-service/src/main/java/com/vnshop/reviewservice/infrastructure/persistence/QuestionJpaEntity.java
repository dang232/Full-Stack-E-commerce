package com.vnshop.reviewservice.infrastructure.persistence;

import com.vnshop.reviewservice.domain.ProductQuestion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(schema = "review_svc", name = "product_questions")
public class QuestionJpaEntity {
    @Id
    @Column(name = "question_id")
    private String questionId;

    @Column(name = "product_id", nullable = false)
    private String productId;

    @Column(name = "buyer_id", nullable = false)
    private String buyerId;

    @Column(name = "question", nullable = false, length = 1000)
    private String question;

    @Column(name = "answer", length = 1000)
    private String answer;

    @Column(name = "answered_at")
    private Instant answeredAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected QuestionJpaEntity() {
    }

    public static QuestionJpaEntity fromDomain(ProductQuestion question) {
        QuestionJpaEntity entity = new QuestionJpaEntity();
        entity.questionId = question.questionId();
        entity.productId = question.productId();
        entity.buyerId = question.buyerId();
        entity.question = question.question();
        entity.answer = question.answer();
        entity.answeredAt = question.answeredAt();
        entity.createdAt = question.createdAt();
        return entity;
    }

    public ProductQuestion toDomain() {
        return new ProductQuestion(questionId, productId, buyerId, question, answer, answeredAt, createdAt);
    }
}
