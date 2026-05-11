package com.vnshop.productservice.infrastructure.web;

import com.vnshop.productservice.domain.Money;
import com.vnshop.productservice.domain.ProductVariant;
import java.math.BigDecimal;

public record VariantRequest(String sku, String name, BigDecimal priceAmount, String priceCurrency, String imageUrl) {
    ProductVariant toDomain() {
        return new ProductVariant(sku, name, new Money(priceAmount, priceCurrency), imageUrl);
    }
}
