package com.vnshop.userservice.application;

import java.util.List;

public record PublicSellersPage(
        List<PublicSellerView> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {}
