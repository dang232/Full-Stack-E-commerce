package com.vnshop.productservice.infrastructure.web.review;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AskQuestionRequest(@NotBlank String productId, @NotBlank String buyerId,
        @NotBlank @Size(max = 1000) String question) {
}
