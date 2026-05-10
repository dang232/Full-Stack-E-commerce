package com.vnshop.orderservice.domain.port.out;

import com.vnshop.orderservice.domain.Order;

public interface OrderEventPublisherPort {
    void publishOrderCreated(Order order);

    void publishOrderUpdated(Order order);
}
