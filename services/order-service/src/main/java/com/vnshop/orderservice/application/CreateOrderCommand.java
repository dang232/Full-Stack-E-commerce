package com.vnshop.orderservice.application;

import com.vnshop.orderservice.domain.Address;
import com.vnshop.orderservice.domain.OrderItem;

import java.util.List;

public record CreateOrderCommand(String buyerId, Address shippingAddress, List<OrderItem> items, String idempotencyKey) {
}
