package com.vnshop.productservice.infrastructure.web.review;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Buyer-supplied review payload. The buyerId comes from the JWT
 * (JwtPrincipalUtil.currentUserId), not the body — anything else would let a
 * caller post reviews as someone else. {@code orderId} is optional because the
 * FE doesn't always have it (review-from-product-page flow). The body field
 * is named {@code comment} but {@code text} is accepted via JsonAlias for
 * back-compat with older callers.
 */
public record CreateReviewRequest(
        @NotBlank String productId,
        String orderId,
        @Min(1) @Max(5) int rating,
        @JsonAlias({"text", "body"}) @Size(max = 1000) String comment,
        @Size(max = 5) List<String> images) {
}

