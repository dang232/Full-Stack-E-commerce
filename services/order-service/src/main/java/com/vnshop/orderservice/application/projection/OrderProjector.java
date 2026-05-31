package com.vnshop.orderservice.application.projection;

import com.vnshop.orderservice.infrastructure.persistence.OrderSummaryProjectionJpaEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.Instant;

@Service
public class OrderProjector {
    private static final Logger LOG = LoggerFactory.getLogger(OrderProjector.class);

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public void upsert(String orderId, String status, String buyerId, String sellerId, BigDecimal totalAmount, int itemCount) {
        OrderSummaryProjectionJpaEntity existing = entityManager.find(OrderSummaryProjectionJpaEntity.class, orderId);
        Instant now = Instant.now();

        if (existing != null) {
            existing.setStatus(status);
            existing.setUpdatedAt(now);
            if (buyerId != null) existing.setBuyerId(buyerId);
            if (sellerId != null) existing.setSellerId(sellerId);
            if (totalAmount != null) existing.setTotalAmount(totalAmount);
            if (itemCount > 0) existing.setItemCount(itemCount);
            entityManager.merge(existing);
            LOG.debug("Updated order_summary for order {}", orderId);
        } else {
            OrderSummaryProjectionJpaEntity entity = new OrderSummaryProjectionJpaEntity();
            entity.setOrderId(orderId);
            entity.setStatus(status);
            entity.setBuyerId(buyerId);
            entity.setSellerId(sellerId);
            entity.setTotalAmount(totalAmount != null ? totalAmount : BigDecimal.ZERO);
            entity.setItemCount(itemCount);
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            entityManager.persist(entity);
            LOG.info("Created order_summary for order {}", orderId);
        }
    }
}
