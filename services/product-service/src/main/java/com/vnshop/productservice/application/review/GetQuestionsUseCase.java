package com.vnshop.productservice.application.review;

import com.vnshop.productservice.domain.review.ProductQuestion;
import com.vnshop.productservice.domain.review.port.out.ReviewRepositoryPort;

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
