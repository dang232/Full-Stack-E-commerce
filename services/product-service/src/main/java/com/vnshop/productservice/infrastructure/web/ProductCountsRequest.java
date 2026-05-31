package com.vnshop.productservice.infrastructure.web;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record ProductCountsRequest(
        @NotEmpty(message = "sellerIds must not be empty")
        @Size(max = 100, message = "sellerIds must not exceed 100 entries")
        Set<String> sellerIds
) {
}
