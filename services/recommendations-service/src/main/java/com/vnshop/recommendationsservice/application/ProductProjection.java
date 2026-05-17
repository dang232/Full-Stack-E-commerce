package com.vnshop.recommendationsservice.application;

import java.math.BigDecimal;
import java.util.List;

/**
 * BE-internal product projection consumed by recommendations-service. Only
 * the fields the recommendation flows actually use are materialized here —
 * the upstream {@code ProductResponse} carries variants, images, etc. that
 * we deliberately drop server-side rather than ship to the FE through us.
 *
 * <p>{@code price} is the lowest variant price (in product-service's currency)
 * and is what the price-proximity filter in
 * {@link YouMayAlsoLikeUseCase} compares against.
 */
public record ProductProjection(
        String id,
        String sellerId,
        String name,
        String categoryId,
        String image,
        BigDecimal price,
        BigDecimal originalPrice,
        Integer reviewCount,
        Double rating,
        Integer sold,
        List<String> images
) {
}
