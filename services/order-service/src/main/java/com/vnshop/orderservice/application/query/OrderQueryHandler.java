package com.vnshop.orderservice.application.query;

import com.vnshop.orderservice.domain.projection.OrderSummaryProjection;
import com.vnshop.orderservice.infrastructure.persistence.OrderSummaryProjectionJpaEntity;
import org.springframework.stereotype.Service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Optional;

@Service
public class OrderQueryHandler {

    @PersistenceContext
    private EntityManager entityManager;

    public List<OrderSummaryProjection> findByBuyerId(String buyerId) {
        return entityManager
            .createQuery("SELECT o FROM OrderSummaryProjectionJpaEntity o WHERE o.buyerId = :buyerId ORDER BY o.createdAt DESC", OrderSummaryProjectionJpaEntity.class)
            .setParameter("buyerId", buyerId)
            .getResultStream()
            .map(OrderSummaryProjectionJpaEntity::toDomain)
            .toList();
    }

    public Optional<OrderSummaryProjection> findByOrderId(String orderId) {
        OrderSummaryProjectionJpaEntity entity = entityManager.find(OrderSummaryProjectionJpaEntity.class, orderId);
        return Optional.ofNullable(entity).map(OrderSummaryProjectionJpaEntity::toDomain);
    }
}
