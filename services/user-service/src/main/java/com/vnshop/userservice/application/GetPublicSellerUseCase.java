package com.vnshop.userservice.application;

import com.vnshop.userservice.domain.SellerNotFoundException;
import com.vnshop.userservice.domain.port.out.SellerStatsPort;
import com.vnshop.userservice.domain.port.out.UserRepositoryPort;

public class GetPublicSellerUseCase {

    private final UserRepositoryPort userRepositoryPort;
    private final SellerStatsPort sellerStatsPort;

    public GetPublicSellerUseCase(UserRepositoryPort userRepositoryPort, SellerStatsPort sellerStatsPort) {
        this.userRepositoryPort = userRepositoryPort;
        this.sellerStatsPort = sellerStatsPort;
    }

    public PublicSellerView view(String sellerId) {
        var seller = userRepositoryPort.findSellerById(sellerId)
                .orElseThrow(() -> new SellerNotFoundException(sellerId));

        SellerStatsPort.SellerStats stats = sellerStatsPort.sellerStats(sellerId);
        long products = sellerStatsPort.productCount(sellerId);

        return new PublicSellerView(
                seller.id(),
                seller.shopName(),
                seller.description(),
                seller.logoUrl(),
                seller.bannerUrl(),
                seller.createdAt(),
                seller.tier().name(),
                stats.ratingAvg(),
                stats.ratingCount(),
                products
        );
    }
}
