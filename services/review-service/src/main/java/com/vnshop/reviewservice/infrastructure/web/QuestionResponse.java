package com.vnshop.reviewservice.infrastructure.web;

import com.vnshop.reviewservice.domain.ProductQuestion;

import java.time.Instant;

public record QuestionResponse(String questionId, String productId, String buyerId, String question,
        String answer, Instant answeredAt, Instant createdAt) {
    static QuestionResponse fromDomain(ProductQuestion question) {
        return new QuestionResponse(question.questionId().toString(), question.productId(), question.buyerId(),
                question.question(), question.answer(), question.answeredAt(), question.createdAt());
    }
}
