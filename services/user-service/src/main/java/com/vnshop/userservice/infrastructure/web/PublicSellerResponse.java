package com.vnshop.userservice.infrastructure.web;

import com.vnshop.userservice.application.PublicSellerView;

import java.time.Instant;

public record PublicSellerResponse(
        String id,
        String shopName,
        String description,
        String logoUrl,
        String bannerUrl,
        String tier,
        Instant joinedAt,
        Double ratingAvg,
        long ratingCount,
        long totalProducts
) {
    public static PublicSellerResponse fromView(PublicSellerView view) {
        return new PublicSellerResponse(
                view.sellerId(),
                view.shopName(),
                view.description(),
                view.logoUrl(),
                view.bannerUrl(),
                view.tier(),
                view.joinedAt(),
                view.ratingAvg(),
                view.ratingCount(),
                view.totalProducts()
        );
    }
}
