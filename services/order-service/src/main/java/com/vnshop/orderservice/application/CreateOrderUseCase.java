package com.vnshop.orderservice.application;

import com.vnshop.orderservice.domain.Address;
import com.vnshop.orderservice.domain.Order;
import com.vnshop.orderservice.domain.OrderItem;
import com.vnshop.orderservice.domain.SubOrder;
import com.vnshop.orderservice.domain.port.out.InventoryReservationPort;
import com.vnshop.orderservice.domain.port.out.OrderEventPublisherPort;
import com.vnshop.orderservice.domain.port.out.OrderRepositoryPort;
import com.vnshop.orderservice.domain.port.out.PaymentRequestPort;
import com.vnshop.orderservice.domain.port.out.ShippingRequestPort;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

public class CreateOrderUseCase {
    private final OrderRepositoryPort orderRepository;
    private final InventoryReservationPort inventoryReservationPort;
    private final PaymentRequestPort paymentRequestPort;
    private final ShippingRequestPort shippingRequestPort;
    private final OrderEventPublisherPort orderEventPublisherPort;

    public CreateOrderUseCase(
            OrderRepositoryPort orderRepository,
            InventoryReservationPort inventoryReservationPort,
            PaymentRequestPort paymentRequestPort,
            ShippingRequestPort shippingRequestPort,
            OrderEventPublisherPort orderEventPublisherPort
    ) {
        this.orderRepository = Objects.requireNonNull(orderRepository, "orderRepository is required");
        this.inventoryReservationPort = Objects.requireNonNull(inventoryReservationPort, "inventoryReservationPort is required");
        this.paymentRequestPort = Objects.requireNonNull(paymentRequestPort, "paymentRequestPort is required");
        this.shippingRequestPort = Objects.requireNonNull(shippingRequestPort, "shippingRequestPort is required");
        this.orderEventPublisherPort = Objects.requireNonNull(orderEventPublisherPort, "orderEventPublisherPort is required");
    }

    public Order create(CreateOrderCommand command) {
        requireNonBlank(command.buyerId(), "buyerId");
        requireNonBlank(command.idempotencyKey(), "idempotencyKey");
        Objects.requireNonNull(command.shippingAddress(), "shippingAddress is required");
        if (command.items() == null || command.items().isEmpty()) {
            throw new IllegalArgumentException("items must not be empty");
        }

        return orderRepository.findByIdempotencyKey(command.idempotencyKey())
                .orElseGet(() -> createNewOrder(command.buyerId(), command.shippingAddress(), command.items(), command.idempotencyKey()));
    }

    private Order createNewOrder(String buyerId, Address shippingAddress, List<OrderItem> items, String idempotencyKey) {
        List<OrderItem> itemSnapshot = List.copyOf(items);
        List<SubOrder> subOrders = splitBySeller(itemSnapshot);
        Order order = new Order(UUID.randomUUID(), buyerId, shippingAddress, subOrders, idempotencyKey);

        boolean inventoryReserved = false;
        boolean paymentRequested = false;
        try {
            inventoryReservationPort.reserve(order.id().toString(), itemSnapshot);
            inventoryReserved = true;
            paymentRequestPort.requestPayment(order.id().toString(), order.paymentMethod(), order.finalAmount());
            paymentRequested = true;
            for (SubOrder subOrder : order.subOrders()) {
                shippingRequestPort.requestShipping(order.id().toString(), subOrder, shippingAddress);
            }
            Order savedOrder = orderRepository.save(order);
            orderEventPublisherPort.publishOrderCreated(savedOrder);
            return savedOrder;
        } catch (RuntimeException failure) {
            compensate(order.id(), inventoryReserved, paymentRequested);
            throw failure;
        }
    }

    private List<SubOrder> splitBySeller(List<OrderItem> items) {
        Map<String, List<OrderItem>> itemsBySeller = items.stream()
                .collect(Collectors.groupingBy(OrderItem::sellerId, Collectors.toList()));
        List<SubOrder> subOrders = new ArrayList<>();
        for (Map.Entry<String, List<OrderItem>> entry : itemsBySeller.entrySet()) {
            subOrders.add(new SubOrder(entry.getKey(), entry.getValue()));
        }
        return List.copyOf(subOrders);
    }

    private void compensate(UUID orderId, boolean inventoryReserved, boolean paymentRequested) {
        if (inventoryReserved) {
            inventoryReservationPort.release(orderId.toString());
        }
        if (paymentRequested) {
            orderRepository.findById(orderId).ifPresent(Order::markPaymentFailed);
        }
    }

    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }
}
