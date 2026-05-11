package com.vnshop.reviewservice.infrastructure.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AnswerQuestionRequest(@NotBlank @Size(max = 1000) String answer) {
}
