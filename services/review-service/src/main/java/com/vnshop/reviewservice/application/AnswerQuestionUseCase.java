package com.vnshop.reviewservice.application;

import com.vnshop.reviewservice.domain.ProductQuestion;
import com.vnshop.reviewservice.domain.port.out.ReviewRepositoryPort;

import java.util.Objects;
import java.util.UUID;

public class AnswerQuestionUseCase {
    private final ReviewRepositoryPort reviewRepositoryPort;

    public AnswerQuestionUseCase(ReviewRepositoryPort reviewRepositoryPort) {
        this.reviewRepositoryPort = Objects.requireNonNull(reviewRepositoryPort, "reviewRepositoryPort is required");
    }

    public ProductQuestion answer(UUID questionId, String answer) {
        ProductQuestion question = reviewRepositoryPort.findQuestionById(questionId)
                .orElseThrow(() -> new IllegalArgumentException("question not found: " + questionId));
        return reviewRepositoryPort.saveQuestion(question.withAnswer(answer));
    }
}
