package com.vnshop.userservice.application;

import java.time.Instant;

public record PublicSellerView(
        String sellerId,
        String shopName,
        String description,
        String logoUrl,
        String bannerUrl,
        Instant joinedAt,
        String tier,
        Double ratingAvg,
        long ratingCount,
        long totalProducts
) {}
