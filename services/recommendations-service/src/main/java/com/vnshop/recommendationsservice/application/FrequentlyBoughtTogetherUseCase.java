package com.vnshop.recommendationsservice.application;

import com.vnshop.recommendationsservice.infrastructure.persistence.CoPurchaseJpaEntity;
import com.vnshop.recommendationsservice.infrastructure.persistence.CoPurchaseRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.data.domain.PageRequest;

/**
 * "Frequently bought together" recommender. We take the top-N products that
 * have appeared in the same orders as the source product (ordered by their
 * co-purchase count) and enrich each one via product-service. Co-purchase
 * counts are maintained by the {@code order.created} Kafka listener — see
 * the recommendations-service infrastructure layer.
 *
 * <p>If an enrichment lookup misses (e.g. the product was deleted) we
 * silently drop the row from the response. We never want to surface a
 * stale id with no name/image to the FE.
 */
public class FrequentlyBoughtTogetherUseCase {
    private final CoPurchaseRepository coPurchaseRepository;
    private final ProductServicePort productServicePort;

    public FrequentlyBoughtTogetherUseCase(
            CoPurchaseRepository coPurchaseRepository,
            ProductServicePort productServicePort
    ) {
        this.coPurchaseRepository = Objects.requireNonNull(coPurchaseRepository, "coPurchaseRepository is required");
        this.productServicePort = Objects.requireNonNull(productServicePort, "productServicePort is required");
    }

    public List<ProductProjection> findFor(String productId, int limit) {
        if (productId == null || productId.isBlank()) {
            throw new IllegalArgumentException("productId is required");
        }
        if (limit <= 0) {
            return List.of();
        }
        List<CoPurchaseJpaEntity> rows = coPurchaseRepository
                .findTopByProductA(productId, PageRequest.of(0, limit));
        if (rows.isEmpty()) {
            return List.of();
        }
        List<ProductProjection> enriched = new ArrayList<>(rows.size());
        for (CoPurchaseJpaEntity row : rows) {
            productServicePort.findById(row.productB()).ifPresent(enriched::add);
        }
        return List.copyOf(enriched);
    }
}
