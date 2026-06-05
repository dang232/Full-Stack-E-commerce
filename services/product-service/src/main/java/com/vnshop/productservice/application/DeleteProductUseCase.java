package com.vnshop.productservice.application;

import com.vnshop.productservice.domain.Product;
import com.vnshop.productservice.domain.ProductEvent;
import com.vnshop.productservice.domain.port.out.ProductEventPublisherPort;
import com.vnshop.productservice.domain.port.out.ProductRepositoryPort;

import java.util.Objects;
import java.util.UUID;

public class DeleteProductUseCase {
    private final ProductRepositoryPort productRepositoryPort;
    private final ProductEventPublisherPort productEventPublisherPort;

    public DeleteProductUseCase(ProductRepositoryPort productRepositoryPort,
                                ProductEventPublisherPort productEventPublisherPort) {
        this.productRepositoryPort = Objects.requireNonNull(productRepositoryPort, "productRepositoryPort is required");
        this.productEventPublisherPort = Objects.requireNonNull(productEventPublisherPort, "productEventPublisherPort is required");
    }

    public void delete(UUID productId, String sellerId) {
        Product product = productRepositoryPort.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("product not found"));

        if (!product.sellerId().equals(sellerId)) {
            throw new IllegalArgumentException("product does not belong to seller");
        }

        product.softDelete();
        productRepositoryPort.save(product);
        productEventPublisherPort.publish(new ProductEvent(
                product.productId().toString(),
                ProductEvent.EventType.DELETED,
                null,
                java.util.Map.of("sellerId", product.sellerId())
        ));
    }
}
