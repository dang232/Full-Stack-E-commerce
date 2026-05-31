package com.vnshop.productservice.infrastructure.web.review;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record SellerSummariesRequest(
        @NotEmpty(message = "sellerIds must not be empty")
        @Size(max = 100, message = "sellerIds must not exceed 100 entries")
        Set<String> sellerIds
) {
}
