package com.vnshop.orderservice.infrastructure.persistence;

import com.vnshop.orderservice.domain.port.out.OrderSummaryQueryPort;
import com.vnshop.orderservice.domain.projection.OrderSummaryProjection;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class OrderSummaryQueryPortAdapter implements OrderSummaryQueryPort {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<OrderSummaryProjection> findByBuyerId(String buyerId) {
        return entityManager
            .createQuery("SELECT o FROM OrderSummaryProjectionJpaEntity o WHERE o.buyerId = :buyerId ORDER BY o.createdAt DESC", OrderSummaryProjectionJpaEntity.class)
            .setParameter("buyerId", buyerId)
            .getResultStream()
            .map(OrderSummaryProjectionJpaEntity::toDomain)
            .toList();
    }

    @Override
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

    @Override
    public Optional<OrderSummaryProjection> findByOrderId(String orderId) {
        OrderSummaryProjectionJpaEntity entity = entityManager.find(OrderSummaryProjectionJpaEntity.class, orderId);
        return Optional.ofNullable(entity).map(OrderSummaryProjectionJpaEntity::toDomain);
    }
}
