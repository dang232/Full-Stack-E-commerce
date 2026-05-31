package com.vnshop.recommendationsservice.infrastructure.web;

import com.vnshop.recommendationsservice.application.ProductProjection;
import java.math.BigDecimal;

/**
 * Wire shape for both recommendation endpoints. Field names match the
 * subset of {@link com.vnshop.recommendationsservice.application.ProductProjection}
 * that the FE renders — keeping them aligned with product-service's
 * {@code ProductResponse} so the FE recommendations cards reuse the same
 * Zod schema as product summaries (see {@code productSummarySchema}).
 */
public record RecommendationItem(
        String id,
        String name,
        String image,
        BigDecimal price,
        BigDecimal originalPrice,
        String sellerId,
        String category,
        Integer reviewCount,
        Double rating,
        Integer sold
) {
    public static RecommendationItem fromProjection(ProductProjection projection) {
        return new RecommendationItem(
                projection.id(),
                projection.name(),
                projection.image(),
                projection.price(),
                projection.originalPrice(),
                projection.sellerId(),
                projection.categoryId(),
                projection.reviewCount(),
                projection.rating(),
                projection.sold()
        );
    }
}
