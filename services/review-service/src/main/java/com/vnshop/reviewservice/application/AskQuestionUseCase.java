package com.vnshop.reviewservice.application;

import com.vnshop.reviewservice.domain.ProductQuestion;
import com.vnshop.reviewservice.domain.port.out.ReviewRepositoryPort;

import java.util.Objects;

public class AskQuestionUseCase {
    private final ReviewRepositoryPort reviewRepositoryPort;

    public AskQuestionUseCase(ReviewRepositoryPort reviewRepositoryPort) {
        this.reviewRepositoryPort = Objects.requireNonNull(reviewRepositoryPort, "reviewRepositoryPort is required");
    }

    public ProductQuestion ask(String productId, String buyerId, String question) {
        return reviewRepositoryPort.saveQuestion(ProductQuestion.asked(productId, buyerId, question));
    }
}
