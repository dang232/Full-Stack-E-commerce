package com.vnshop.reviewservice.infrastructure.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Pt14 audit fix: buyerId removed from the wire shape. Controller
 * resolves it from the JWT principal.
 */
public record AskQuestionRequest(@NotBlank String productId,
        @NotBlank @Size(max = 1000) String question) {
}
