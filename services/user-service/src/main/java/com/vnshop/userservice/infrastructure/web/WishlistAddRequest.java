package com.vnshop.userservice.infrastructure.web;

import jakarta.validation.constraints.NotBlank;

public record WishlistAddRequest(@NotBlank String productId) {}
