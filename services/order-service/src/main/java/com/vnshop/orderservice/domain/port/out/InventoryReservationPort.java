package com.vnshop.orderservice.domain.port.out;

import com.vnshop.orderservice.domain.OrderItem;

import java.util.List;

public interface InventoryReservationPort {
    void reserve(String orderId, List<OrderItem> items);

    void release(String orderId);

    /**
     * Extend the TTL of an inventory reservation by {@code extraMinutes}.
     * Adapters that do not support TTL extension should throw
     * {@link UnsupportedOperationException}; callers must handle that case.
     */
    default void extendReservation(String orderId, int extraMinutes) {
        throw new UnsupportedOperationException("extendReservation not supported by this adapter");
    }
}
