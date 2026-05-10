package com.vnshop.reviewservice.application;

import com.vnshop.reviewservice.domain.ProductQuestion;
import com.vnshop.reviewservice.domain.port.out.ReviewRepositoryPort;

import java.util.List;
import java.util.Objects;

public class GetQuestionsUseCase {
    private final ReviewRepositoryPort reviewRepositoryPort;

    public GetQuestionsUseCase(ReviewRepositoryPort reviewRepositoryPort) {
        this.reviewRepositoryPort = Objects.requireNonNull(reviewRepositoryPort, "reviewRepositoryPort is required");
    }

    public List<ProductQuestion> get(String productId) {
        return reviewRepositoryPort.findQuestionsByProductId(productId);
    }
}
