package com.vnshop.userservice.application;

import com.vnshop.userservice.domain.SellerProfile;
import com.vnshop.userservice.domain.port.out.SellerStatsPort;
import com.vnshop.userservice.domain.port.out.UserRepositoryPort;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ListPublicSellersUseCase {

    private static final int MAX_PAGE_SIZE = 50;

    private final UserRepositoryPort userRepositoryPort;
    private final SellerStatsPort sellerStatsPort;

    public ListPublicSellersUseCase(UserRepositoryPort userRepositoryPort, SellerStatsPort sellerStatsPort) {
        this.userRepositoryPort = userRepositoryPort;
        this.sellerStatsPort = sellerStatsPort;
    }

    public PublicSellersPage list(int page, int size) {
        if (page < 0) throw new IllegalArgumentException("page must be >= 0");
        if (size <= 0) throw new IllegalArgumentException("size must be > 0");
        int cappedSize = Math.min(size, MAX_PAGE_SIZE);

        List<SellerProfile> sellers = userRepositoryPort.findApprovedSellers(page, cappedSize);
        long totalElements = userRepositoryPort.countApprovedSellers();
        int totalPages = (int) Math.ceil((double) totalElements / cappedSize);

        // Single batch round-trip per page (down from 2*pageSize HTTP calls).
        // The adapter caches by sellerId so repeat traffic on the showcase
        // bypasses product-service entirely.
        Set<String> sellerIds = new HashSet<>();
        for (SellerProfile s : sellers) sellerIds.add(s.id());
        Map<String, SellerStatsPort.SellerStats> stats = sellerStatsPort.sellerStatsBatch(sellerIds);
        Map<String, Long> productCounts = sellerStatsPort.productCountBatch(sellerIds);

        List<PublicSellerView> content = sellers.stream()
                .map(seller -> {
                    SellerStatsPort.SellerStats s = stats.getOrDefault(seller.id(), SellerStatsPort.SellerStats.empty());
                    long products = productCounts.getOrDefault(seller.id(), 0L);
                    return new PublicSellerView(
                            seller.id(),
                            seller.shopName(),
                            seller.description(),
                            seller.logoUrl(),
                            seller.bannerUrl(),
                            seller.createdAt(),
                            seller.tier().name(),
                            s.ratingAvg(),
                            s.ratingCount(),
                            products
                    );
                })
                .toList();

        return new PublicSellersPage(content, page, cappedSize, totalElements, totalPages);
    }
}
