package com.vnshop.recommendationsservice.application;

import java.util.List;
import java.util.Optional;

/**
 * BE→BE adapter port to product-service. Only two read paths are needed:
 * fetch one product by id, and list a category's catalog so we can filter
 * by price proximity. Both calls bypass the gateway and go straight to the
 * product-service URL configured via {@code vnshop.recommendations.product-service-url}.
 *
 * <p>The recommendations endpoints are public (no JWT required), and so are
 * product-service's read paths, so we don't propagate auth headers.
 */
public interface ProductServicePort {
    Optional<ProductProjection> findById(String productId);

    /**
     * Return up to {@code limit} products in the given category sorted by
     * popularity (sales volume / rating). Pagination is product-service's
     * native page parameter; we never need cross-page assembly.
     */
    List<ProductProjection> listByCategory(String categoryId, int limit);
}
