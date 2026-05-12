package com.vnshop.productservice.infrastructure.web.review;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AnswerQuestionRequest(@NotBlank @Size(max = 1000) String answer) {
}
