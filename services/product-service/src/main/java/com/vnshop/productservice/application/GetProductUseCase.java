package com.vnshop.productservice.application;

import com.vnshop.productservice.domain.port.out.ProductRepositoryPort;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class GetProductUseCase {
    private final ProductRepositoryPort productRepositoryPort;

    public GetProductUseCase(ProductRepositoryPort productRepositoryPort) {
        this.productRepositoryPort = Objects.requireNonNull(productRepositoryPort, "productRepositoryPort is required");
    }

    public ProductResponse findById(UUID productId) {
        return productRepositoryPort.findById(productId)
                .map(ProductResponse::fromDomain)
                .orElseThrow(() -> new IllegalArgumentException("product not found"));
    }

    public List<ProductResponse> findBySeller(String sellerId) {
        return productRepositoryPort.findBySellerId(sellerId).stream().map(ProductResponse::fromDomain).toList();
    }

    public List<ProductResponse> findByCategory(String categoryId) {
        return productRepositoryPort.findByCategory(categoryId).stream().map(ProductResponse::fromDomain).toList();
    }

    public List<ProductResponse> searchByName(String name) {
        return productRepositoryPort.searchByName(name).stream().map(ProductResponse::fromDomain).toList();
    }

    public List<String> findCategories() {
        return productRepositoryPort.findDistinctCategories();
    }
}
