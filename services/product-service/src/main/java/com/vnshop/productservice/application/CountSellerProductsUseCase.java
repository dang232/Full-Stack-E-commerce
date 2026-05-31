package com.vnshop.productservice.application;

import com.vnshop.productservice.domain.port.out.ProductRepositoryPort;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class CountSellerProductsUseCase {
    private final ProductRepositoryPort productRepositoryPort;

    public CountSellerProductsUseCase(ProductRepositoryPort productRepositoryPort) {
        this.productRepositoryPort = Objects.requireNonNull(productRepositoryPort, "productRepositoryPort is required");
    }

    public long count(String sellerId) {
        return productRepositoryPort.countBySellerId(sellerId);
    }

    public Map<String, Long> countAll(Set<String> sellerIds) {
        validateSellerIds(sellerIds);
        return productRepositoryPort.countBySellerIds(sellerIds);
    }

    private void validateSellerIds(Set<String> sellerIds) {
        if (sellerIds == null) {
            throw new IllegalArgumentException("sellerIds must not be null");
        }
        if (sellerIds.isEmpty()) {
            throw new IllegalArgumentException("sellerIds must not be empty");
        }
        if (sellerIds.size() > 100) {
            throw new IllegalArgumentException("sellerIds must not exceed 100 entries");
        }
        for (String id : sellerIds) {
            if (id == null) {
                throw new IllegalArgumentException("sellerIds must not contain null entries");
            }
        }
    }
}
