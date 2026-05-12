package com.vnshop.productservice.domain.review;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
@Getter
@Setter
public class ProductQuestion {
    private final UUID questionId;
    private final String productId;
    private final String buyerId;
    private final String question;
    private final String answer;
    private final Instant answeredAt;
    private final Instant createdAt;

    public ProductQuestion(UUID questionId, String productId, String buyerId, String question, String answer,
            Instant answeredAt, Instant createdAt) {
        this.questionId = Objects.requireNonNull(questionId, "questionId is required");
        this.productId = requireNonBlank(productId, "productId");
        this.buyerId = requireNonBlank(buyerId, "buyerId");
        this.question = requireNonBlank(question, "question");
        this.answer = answer;
        this.answeredAt = answeredAt;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt is required");
    }

    public static ProductQuestion asked(String productId, String buyerId, String question) {
        return new ProductQuestion(UUID.randomUUID(), productId, buyerId, question, null, null, Instant.now());
    }

    public ProductQuestion withAnswer(String nextAnswer) {
        return new ProductQuestion(questionId, productId, buyerId, question, requireNonBlank(nextAnswer, "answer"), Instant.now(), createdAt);
    }

    public UUID questionId() {
        return questionId;
    }

    public String productId() {
        return productId;
    }

    public String buyerId() {
        return buyerId;
    }

    public String question() {
        return question;
    }

    public String answer() {
        return answer;
    }

    public Instant answeredAt() {
        return answeredAt;
    }

    public Instant createdAt() {
        return createdAt;
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
    }
}
