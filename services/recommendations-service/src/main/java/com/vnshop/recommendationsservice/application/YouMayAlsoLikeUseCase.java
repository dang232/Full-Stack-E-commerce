package com.vnshop.recommendationsservice.application;

import java.util.List;
import java.util.Objects;

/**
 * "You may also like" recommender. The flow:
 *  1. Fetch the source product so we know its category and price.
 *  2. List up to {@code candidatePool} products in the same category.
 *  3. Drop the source product itself; drop products outside the
 *     {@code priceProximityPercent}% price band; rank what remains by
 *     sold count desc, then rating desc.
 *  4. Cap the result to the requested {@code limit}.
 *
 * <p>The category and price proximity filter is intentionally simple — the
 * audit handover called this a stub, not Amazon's recommender. If a category
 * has fewer items than {@code limit} after the price filter, we return the
 * smaller list rather than padding with off-band candidates.
 */
public class YouMayAlsoLikeUseCase {
    private final ProductServicePort productServicePort;
    private final int priceProximityPercent;
    private final int candidatePool;

    public YouMayAlsoLikeUseCase(
            ProductServicePort productServicePort,
            int priceProximityPercent,
            int candidatePool
    ) {
        this.productServicePort = Objects.requireNonNull(productServicePort, "productServicePort is required");
        if (priceProximityPercent <= 0 || priceProximityPercent >= 100) {
            throw new IllegalArgumentException("priceProximityPercent must be in (0, 100)");
        }
        if (candidatePool <= 0) {
            throw new IllegalArgumentException("candidatePool must be > 0");
        }
        this.priceProximityPercent = priceProximityPercent;
        this.candidatePool = candidatePool;
    }

    public List<ProductProjection> findFor(String productId, int limit) {
        if (productId == null || productId.isBlank()) {
            throw new IllegalArgumentException("productId is required");
        }
        if (limit <= 0) {
            return List.of();
        }
        ProductProjection source = productServicePort.findById(productId).orElse(null);
        if (source == null || source.categoryId() == null || source.categoryId().isBlank()) {
            return List.of();
        }
        java.math.BigDecimal sourcePrice = source.price();
        java.math.BigDecimal lowerBound = null;
        java.math.BigDecimal upperBound = null;
        if (sourcePrice != null && sourcePrice.signum() > 0) {
            java.math.BigDecimal band = sourcePrice
                    .multiply(java.math.BigDecimal.valueOf(priceProximityPercent))
                    .divide(java.math.BigDecimal.valueOf(100));
            lowerBound = sourcePrice.subtract(band);
            upperBound = sourcePrice.add(band);
        }
        java.math.BigDecimal lowerFinal = lowerBound;
        java.math.BigDecimal upperFinal = upperBound;
        return productServicePort.listByCategory(source.categoryId(), candidatePool).stream()
                .filter(p -> !productId.equals(p.id()))
                .filter(p -> withinPriceBand(p.price(), lowerFinal, upperFinal))
                .sorted((a, b) -> {
                    int bySold = Integer.compare(orZero(b.sold()), orZero(a.sold()));
                    if (bySold != 0) return bySold;
                    return Double.compare(orZero(b.rating()), orZero(a.rating()));
                })
                .limit(limit)
                .toList();
    }

    private static boolean withinPriceBand(
            java.math.BigDecimal candidate,
            java.math.BigDecimal lowerBound,
            java.math.BigDecimal upperBound
    ) {
        // No source price means no band — fall back to "anything in the same category".
        if (lowerBound == null || upperBound == null) {
            return true;
        }
        if (candidate == null || candidate.signum() <= 0) {
            return false;
        }
        return candidate.compareTo(lowerBound) >= 0 && candidate.compareTo(upperBound) <= 0;
    }

    private static int orZero(Integer value) {
        return value == null ? 0 : value;
    }

    private static double orZero(Double value) {
        return value == null ? 0.0 : value;
    }
}
