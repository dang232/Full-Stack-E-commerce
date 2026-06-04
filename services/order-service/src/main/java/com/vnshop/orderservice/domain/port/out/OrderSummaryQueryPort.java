package com.vnshop.orderservice.domain.port.out;

import com.vnshop.orderservice.domain.projection.OrderSummaryProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

/**
 * Port for querying order summary projections from the read model.
 * Decouples query handlers from JPA entity specifics.
 */
public interface OrderSummaryQueryPort {
    List<OrderSummaryProjection> findByBuyerId(String buyerId);
    Page<OrderSummaryProjection> findByBuyerId(String buyerId, String status, Pageable pageable);
    Optional<OrderSummaryProjection> findByOrderId(String orderId);
}
