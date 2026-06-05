package com.vnshop.orderservice.domain.port.out;

import com.vnshop.orderservice.domain.Order;
import com.vnshop.orderservice.domain.SubOrder;

public interface OrderEventPublisherPort {
    void publishOrderCreated(Order order);

    void publishOrderUpdated(Order order);

    void publishOrderPaid(Order order);

    void publishOrderDelivered(Order order, SubOrder subOrder);
}
