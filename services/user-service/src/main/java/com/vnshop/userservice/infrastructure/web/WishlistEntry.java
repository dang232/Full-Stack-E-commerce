package com.vnshop.userservice.infrastructure.web;

import java.time.Instant;

public record WishlistEntry(String productId, Instant createdAt) {}
