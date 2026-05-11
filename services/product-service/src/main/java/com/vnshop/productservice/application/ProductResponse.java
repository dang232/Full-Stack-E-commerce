package com.vnshop.productservice.application;

import com.vnshop.productservice.domain.Product;
import java.math.BigDecimal;
import java.util.List;

public record ProductResponse(
        String id,
        String sellerId,
        String name,
        String description,
        String categoryId,
        String brand,
        String status,
        List<VariantResponse> variants,
        List<ImageResponse> images
) {
    public static ProductResponse fromDomain(Product product) {
        return new ProductResponse(
                product.productId().toString(),
                product.sellerId(),
                product.name(),
                product.description(),
                product.categoryId(),
                product.brand(),
                product.status().name(),
                product.variants().stream()
                        .map(v -> new VariantResponse(v.sku(), v.name(), v.price().amount(), v.price().currency(), v.imageUrl()))
                        .toList(),
                product.images().stream()
                        .map(i -> new ImageResponse(i.url(), i.alt(), i.sortOrder()))
                        .toList()
        );
    }

    public record VariantResponse(String sku, String name, BigDecimal priceAmount, String priceCurrency, String imageUrl) {}

    public record ImageResponse(String url, String alt, int sortOrder) {}
}
