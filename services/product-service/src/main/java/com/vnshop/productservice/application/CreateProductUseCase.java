package com.vnshop.productservice.application;

import com.vnshop.productservice.domain.Product;
import com.vnshop.productservice.domain.ProductEvent;
import com.vnshop.productservice.domain.ProductImage;
import com.vnshop.productservice.domain.ProductVariant;
import com.vnshop.productservice.domain.port.out.ProductEventPublisherPort;
import com.vnshop.productservice.domain.port.out.ProductRepositoryPort;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class CreateProductUseCase {
    private final ProductRepositoryPort productRepositoryPort;
    private final ProductEventPublisherPort productEventPublisherPort;

    public CreateProductUseCase(ProductRepositoryPort productRepositoryPort, ProductEventPublisherPort productEventPublisherPort) {
        this.productRepositoryPort = Objects.requireNonNull(productRepositoryPort, "productRepositoryPort is required");
        this.productEventPublisherPort = Objects.requireNonNull(productEventPublisherPort, "productEventPublisherPort is required");
    }

    public Product create(
            String sellerId,
            String name,
            String description,
            String categoryId,
            String brand,
            List<ProductVariant> variants,
            List<ProductImage> images
    ) {
        Product product = new Product(null, sellerId, name, description, categoryId, brand, variants, images);
        Product saved = productRepositoryPort.save(product);
        productEventPublisherPort.publish(new ProductEvent(
                saved.productId(),
                ProductEvent.EventType.CREATED,
                null,
                Map.of("sellerId", saved.sellerId(), "status", saved.status().name())
        ));
        return saved;
    }
}
