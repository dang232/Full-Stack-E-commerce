package com.vnshop.productservice.infrastructure.web;

import com.vnshop.productservice.domain.Money;
import com.vnshop.productservice.domain.ProductVariant;
import java.math.BigDecimal;

public record VariantRequest(
        String sku,
        String name,
        BigDecimal priceAmount,
        String priceCurrency,
        String imageUrl,
        Integer stockQuantity
) {
    ProductVariant toDomain() {
        int stock = stockQuantity == null ? 0 : stockQuantity;
        return new ProductVariant(sku, name, new Money(priceAmount, priceCurrency), imageUrl, stock);
    }
}
