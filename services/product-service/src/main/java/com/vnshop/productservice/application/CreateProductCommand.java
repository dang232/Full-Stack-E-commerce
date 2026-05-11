package com.vnshop.productservice.application;

import com.vnshop.productservice.domain.ProductImage;
import com.vnshop.productservice.domain.ProductVariant;
import java.util.List;

public record CreateProductCommand(
        String sellerId,
        String name,
        String description,
        String categoryId,
        String brand,
        List<ProductVariant> variants,
        List<ProductImage> images
) {}
