package com.vnshop.productservice.application.review;

import com.vnshop.productservice.application.ProductAccessDeniedException;
import com.vnshop.productservice.domain.Product;
import com.vnshop.productservice.domain.port.out.ProductRepositoryPort;
import com.vnshop.productservice.domain.review.ProductQuestion;
import com.vnshop.productservice.domain.review.port.out.ReviewRepositoryPort;

import java.util.Objects;
import java.util.UUID;

public class AnswerQuestionUseCase {
    private final ReviewRepositoryPort reviewRepositoryPort;
    private final ProductRepositoryPort productRepositoryPort;

    public AnswerQuestionUseCase(ReviewRepositoryPort reviewRepositoryPort,
            ProductRepositoryPort productRepositoryPort) {
        this.reviewRepositoryPort = Objects.requireNonNull(reviewRepositoryPort, "reviewRepositoryPort is required");
        this.productRepositoryPort = Objects.requireNonNull(productRepositoryPort, "productRepositoryPort is required");
    }

    public ProductQuestion answer(UUID questionId, String sellerId, String answer) {
        ProductQuestion question = reviewRepositoryPort.findQuestionById(questionId)
                .orElseThrow(() -> new IllegalArgumentException("question not found: " + questionId));

        Product product = productRepositoryPort.findById(UUID.fromString(question.productId()))
                .orElseThrow(() -> new IllegalArgumentException("product not found: " + question.productId()));

        if (!product.sellerId().equals(sellerId)) {
            throw new ProductAccessDeniedException("only the product seller can answer questions");
        }

        return reviewRepositoryPort.saveQuestion(question.withAnswer(answer));
    }
}
