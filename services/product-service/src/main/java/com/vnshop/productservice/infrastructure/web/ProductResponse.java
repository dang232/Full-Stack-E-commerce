package com.vnshop.productservice.infrastructure.web;

import com.vnshop.productservice.domain.Product;
import java.util.List;

public record ProductResponse(String id, String sellerId, String name, String description, String categoryId, String brand, String status, List<VariantResponse> variants, List<ImageResponse> images) {
    static ProductResponse fromDomain(Product product) {
        return new ProductResponse(
                product.productId().toString(),
                product.sellerId(),
                product.name(),
                product.description(),
                product.categoryId(),
                product.brand(),
                product.status().name(),
                product.variants().stream().map(VariantResponse::fromDomain).toList(),
                product.images().stream().map(ImageResponse::fromDomain).toList()
        );
    }
}
