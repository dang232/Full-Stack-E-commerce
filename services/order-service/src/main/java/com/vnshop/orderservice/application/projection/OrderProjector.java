package com.vnshop.orderservice.application.projection;

import com.vnshop.orderservice.domain.port.out.ProjectionPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class OrderProjector {
    private static final Logger LOG = LoggerFactory.getLogger(OrderProjector.class);

    private final ProjectionPort projectionPort;

    public OrderProjector(ProjectionPort projectionPort) {
        this.projectionPort = projectionPort;
    }

    public void upsert(String orderId, String status, String buyerId, String sellerId, BigDecimal totalAmount, int itemCount) {
        projectionPort.upsertOrderSummary(orderId, status, buyerId, sellerId, totalAmount, itemCount);
        LOG.debug("Projected order_summary for order {}", orderId);
    }
}
