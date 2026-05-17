package com.vnshop.recommendationsservice.application;

import com.vnshop.recommendationsservice.infrastructure.persistence.CoPurchaseJpaEntity;
import com.vnshop.recommendationsservice.infrastructure.persistence.CoPurchaseRepository;
import com.vnshop.recommendationsservice.infrastructure.persistence.ProcessedOrderJpaEntity;
import com.vnshop.recommendationsservice.infrastructure.persistence.ProcessedOrderRepository;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aggregates co-purchase counts when a new order arrives.
 *
 * <p>For every distinct product pair in an order's items list we record the
 * pair both ways — (A,B) and (B,A) — so the read-side query against
 * {@code product_a = ?} always returns the right candidates without having
 * to UNION two indexes. Self-pairs are skipped (a product appearing twice
 * in an order shouldn't co-purchase with itself).
 *
 * <p>Idempotency: each order id is recorded in {@code processed_orders}
 * before counts are touched. A redelivery of the same {@code order.created}
 * event short-circuits at the existence check and never inflates counts.
 *
 * <p>Note: Hibernate's saveAndFlush is sufficient here because each order
 * is processed in a single transaction. Concurrent processing of different
 * orders that touch the same (A,B) pair will serialize on the row lock for
 * that pair — that's fine and correct, and the volume is low for an MVP.
 */
@Service
public class CoPurchaseAggregator {
    private static final Logger LOGGER = LoggerFactory.getLogger(CoPurchaseAggregator.class);

    private final CoPurchaseRepository coPurchaseRepository;
    private final ProcessedOrderRepository processedOrderRepository;

    public CoPurchaseAggregator(
            CoPurchaseRepository coPurchaseRepository,
            ProcessedOrderRepository processedOrderRepository
    ) {
        this.coPurchaseRepository = Objects.requireNonNull(coPurchaseRepository, "coPurchaseRepository is required");
        this.processedOrderRepository = Objects.requireNonNull(processedOrderRepository, "processedOrderRepository is required");
    }

    @Transactional
    public void recordOrder(String orderId, List<String> productIds) {
        if (orderId == null || orderId.isBlank()) {
            LOGGER.warn("Skipping order with blank id");
            return;
        }
        if (productIds == null || productIds.size() < 2) {
            // Single-item orders contribute no co-purchase signal.
            // Mark as processed anyway so a retry doesn't re-evaluate.
            markProcessed(orderId);
            return;
        }
        if (processedOrderRepository.existsById(orderId)) {
            LOGGER.debug("Skipping already-processed order {}", orderId);
            return;
        }

        List<String> distinctProducts = productIds.stream()
                .filter(Objects::nonNull)
                .filter(s -> !s.isBlank())
                .distinct()
                .toList();
        if (distinctProducts.size() < 2) {
            markProcessed(orderId);
            return;
        }

        for (int i = 0; i < distinctProducts.size(); i++) {
            for (int j = i + 1; j < distinctProducts.size(); j++) {
                String a = distinctProducts.get(i);
                String b = distinctProducts.get(j);
                incrementPair(a, b);
                incrementPair(b, a);
            }
        }
        markProcessed(orderId);
        LOGGER.debug("Recorded co-purchases for order {} ({} distinct products)", orderId, distinctProducts.size());
    }

    private void incrementPair(String productA, String productB) {
        CoPurchaseJpaEntity.CoPurchaseId id = new CoPurchaseJpaEntity.CoPurchaseId(productA, productB);
        Optional<CoPurchaseJpaEntity> existing = coPurchaseRepository.findById(id);
        if (existing.isPresent()) {
            CoPurchaseJpaEntity entity = existing.get();
            entity.setCoCount(entity.getCoCount() + 1);
            entity.setLastSeenAt(Instant.now());
            coPurchaseRepository.save(entity);
        } else {
            coPurchaseRepository.save(new CoPurchaseJpaEntity(productA, productB, 1L, Instant.now()));
        }
    }

    private void markProcessed(String orderId) {
        if (!processedOrderRepository.existsById(orderId)) {
            processedOrderRepository.save(new ProcessedOrderJpaEntity(orderId));
        }
    }
}
