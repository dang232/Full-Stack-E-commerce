package com.vnshop.reviewservice.application;

import com.vnshop.reviewservice.domain.ProductQuestion;
import com.vnshop.reviewservice.domain.port.out.ReviewRepositoryPort;

import java.util.Objects;

public class AskQuestionUseCase {
    private final ReviewRepositoryPort reviewRepositoryPort;

    public AskQuestionUseCase(ReviewRepositoryPort reviewRepositoryPort) {
        this.reviewRepositoryPort = Objects.requireNonNull(reviewRepositoryPort, "reviewRepositoryPort is required");
    }

    public ProductQuestion ask(AskQuestionCommand command) {
        return reviewRepositoryPort.saveQuestion(ProductQuestion.asked(command.productId(), command.buyerId(), command.question()));
    }
}
