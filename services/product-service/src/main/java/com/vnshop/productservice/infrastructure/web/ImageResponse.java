package com.vnshop.productservice.infrastructure.web;

import com.vnshop.productservice.domain.ProductImage;

public record ImageResponse(String url, String alt, int sortOrder) {
    static ImageResponse fromDomain(ProductImage image) {
        return new ImageResponse(image.url(), image.alt(), image.sortOrder());
    }
}
