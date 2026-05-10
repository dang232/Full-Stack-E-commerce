package com.vnshop.orderservice.domain.port.out;

import com.vnshop.orderservice.domain.OrderItem;

import java.util.List;

public interface InventoryReservationPort {
    void reserve(String orderId, List<OrderItem> items);

    void release(String orderId);
}
