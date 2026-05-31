package com.vnshop.orderservice.application.query;

import com.vnshop.orderservice.domain.projection.OrderSummaryProjection;
import com.vnshop.orderservice.infrastructure.persistence.OrderSummaryProjectionJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import java.util.List;
import java.util.Optional;

@Service
public class OrderQueryHandler {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Returns all orders for a buyer ordered by createdAt DESC.
     * <p>Kept for callers that want the full list (e.g. invoices, exports).
     */
    public List<OrderSummaryProjection> findByBuyerId(String buyerId) {
        return entityManager
            .createQuery("SELECT o FROM OrderSummaryProjectionJpaEntity o WHERE o.buyerId = :buyerId ORDER BY o.createdAt DESC", OrderSummaryProjectionJpaEntity.class)
            .setParameter("buyerId", buyerId)
            .getResultStream()
            .map(OrderSummaryProjectionJpaEntity::toDomain)
            .toList();
    }

    /**
     * Paged variant with optional status filter. Used by the buyer-facing
     * GET /orders endpoint, which the FE pages through with ?page&size&status.
     */
    public Page<OrderSummaryProjection> findByBuyerId(String buyerId, String status, Pageable pageable) {
        boolean hasStatus = status != null && !status.isBlank();
        String where = "WHERE o.buyerId = :buyerId" + (hasStatus ? " AND o.status = :status" : "");

        TypedQuery<Long> countQuery = entityManager.createQuery(
            "SELECT COUNT(o) FROM OrderSummaryProjectionJpaEntity o " + where, Long.class)
            .setParameter("buyerId", buyerId);
        if (hasStatus) countQuery.setParameter("status", status);
        long total = countQuery.getSingleResult();

        TypedQuery<OrderSummaryProjectionJpaEntity> dataQuery = entityManager.createQuery(
            "SELECT o FROM OrderSummaryProjectionJpaEntity o " + where + " ORDER BY o.createdAt DESC",
            OrderSummaryProjectionJpaEntity.class)
            .setParameter("buyerId", buyerId);
        if (hasStatus) dataQuery.setParameter("status", status);
        dataQuery.setFirstResult((int) pageable.getOffset());
        dataQuery.setMaxResults(pageable.getPageSize());

        List<OrderSummaryProjection> content = dataQuery.getResultStream()
            .map(OrderSummaryProjectionJpaEntity::toDomain)
            .toList();

        return new PageImpl<>(content, pageable, total);
    }

    public Optional<OrderSummaryProjection> findByOrderId(String orderId) {
        OrderSummaryProjectionJpaEntity entity = entityManager.find(OrderSummaryProjectionJpaEntity.class, orderId);
        return Optional.ofNullable(entity).map(OrderSummaryProjectionJpaEntity::toDomain);
    }
}
