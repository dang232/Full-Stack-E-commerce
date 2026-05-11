package com.vnshop.productservice.application;

import com.vnshop.productservice.domain.Product;
import com.vnshop.productservice.domain.port.out.ProductRepositoryPort;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class GetProductUseCase {
    private final ProductRepositoryPort productRepositoryPort;

    public GetProductUseCase(ProductRepositoryPort productRepositoryPort) {
        this.productRepositoryPort = Objects.requireNonNull(productRepositoryPort, "productRepositoryPort is required");
    }

    public Optional<Product> findById(UUID productId) {
        return productRepositoryPort.findById(productId);
    }

    public List<Product> findBySeller(String sellerId) {
        return productRepositoryPort.findBySellerId(sellerId);
    }

    public List<Product> findByCategory(String categoryId) {
        return productRepositoryPort.findByCategory(categoryId);
    }

    public List<Product> searchByName(String name) {
        return productRepositoryPort.searchByName(name);
    }

    public List<String> findCategories() {
        return productRepositoryPort.findDistinctCategories();
    }
}
