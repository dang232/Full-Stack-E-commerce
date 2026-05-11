package com.vnshop.productservice.infrastructure.web;

import com.vnshop.productservice.domain.ProductVariant;
import java.math.BigDecimal;

public record VariantResponse(String sku, String name, BigDecimal priceAmount, String priceCurrency, String imageUrl) {
    static VariantResponse fromDomain(ProductVariant variant) {
        return new VariantResponse(variant.sku(), variant.name(), variant.price().amount(), variant.price().currency(), variant.imageUrl());
    }
}
