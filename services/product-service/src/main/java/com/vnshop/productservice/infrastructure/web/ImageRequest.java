package com.vnshop.productservice.infrastructure.web;

import com.vnshop.productservice.domain.ProductImage;

public record ImageRequest(String url, String alt, int sortOrder) {
    ProductImage toDomain() {
        return new ProductImage(url, alt, sortOrder);
    }
}
