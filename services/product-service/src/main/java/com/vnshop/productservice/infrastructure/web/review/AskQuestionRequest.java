package com.vnshop.productservice.infrastructure.web.review;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Buyer asks a product question. buyerId is read from the JWT, not the body. */
public record AskQuestionRequest(@NotBlank String productId,
        @NotBlank @Size(max = 1000) String question) {
}
