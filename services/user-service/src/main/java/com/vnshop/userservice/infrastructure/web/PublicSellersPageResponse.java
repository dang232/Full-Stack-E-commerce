package com.vnshop.userservice.infrastructure.web;

import com.vnshop.userservice.application.PublicSellersPage;

import java.util.List;

public record PublicSellersPageResponse(
        List<PublicSellerResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static PublicSellersPageResponse fromPage(PublicSellersPage page) {
        List<PublicSellerResponse> content = page.content().stream()
                .map(PublicSellerResponse::fromView)
                .toList();
        return new PublicSellersPageResponse(content, page.page(), page.size(), page.totalElements(), page.totalPages());
    }
}
