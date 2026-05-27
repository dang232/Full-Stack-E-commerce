package com.vnshop.orderservice.infrastructure.event.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vnshop.orderservice.domain.Address;
import com.vnshop.orderservice.domain.FulfillmentStatus;
import com.vnshop.orderservice.domain.Money;
import com.vnshop.orderservice.domain.Order;
import com.vnshop.orderservice.domain.OrderItem;
import com.vnshop.orderservice.domain.PaymentStatus;
import com.vnshop.orderservice.domain.SubOrder;
import com.vnshop.orderservice.domain.port.out.OrderEventPublisherPort;
import com.vnshop.orderservice.domain.port.out.OrderRepositoryPort;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentCompletedListenerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void flipsPaymentStatusAndPublishesOrderPaid() {
        UUID orderId = UUID.randomUUID();
        InMemoryOrderRepo repo = new InMemoryOrderRepo();
        repo.save(pendingOrder(orderId));
        RecordingPublisher publisher = new RecordingPublisher();
        PaymentCompletedListener listener = new PaymentCompletedListener(repo, publisher, objectMapper);

        listener.onPaymentCompleted(eventJson(orderId, "COMPLETED", "PAYPAL"));

        Order saved = repo.findById(orderId).orElseThrow();
        assertThat(saved.paymentStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(publisher.paid).hasSize(1);
        assertThat(publisher.paid.get(0).id()).isEqualTo(orderId);
    }

    @Test
    void idempotentWhenAlreadyCompleted() {
        UUID orderId = UUID.randomUUID();
        InMemoryOrderRepo repo = new InMemoryOrderRepo();
        Order order = pendingOrder(orderId);
        order.markPaymentCompleted();
        repo.save(order);
        repo.saveCount = 0;
        RecordingPublisher publisher = new RecordingPublisher();
        PaymentCompletedListener listener = new PaymentCompletedListener(repo, publisher, objectMapper);

        listener.onPaymentCompleted(eventJson(orderId, "COMPLETED", "PAYPAL"));

        assertThat(publisher.paid).isEmpty();
        assertThat(repo.saveCount).isZero();
    }

    @Test
    void skipsWhenOrderMissing() {
        InMemoryOrderRepo repo = new InMemoryOrderRepo();
        RecordingPublisher publisher = new RecordingPublisher();
        PaymentCompletedListener listener = new PaymentCompletedListener(repo, publisher, objectMapper);

        listener.onPaymentCompleted(eventJson(UUID.randomUUID(), "COMPLETED", "VNPAY"));

        assertThat(publisher.paid).isEmpty();
    }

    @Test
    void skipsNonCompletedStatus() {
        UUID orderId = UUID.randomUUID();
        InMemoryOrderRepo repo = new InMemoryOrderRepo();
        repo.save(pendingOrder(orderId));
        RecordingPublisher publisher = new RecordingPublisher();
        PaymentCompletedListener listener = new PaymentCompletedListener(repo, publisher, objectMapper);

        listener.onPaymentCompleted(eventJson(orderId, "FAILED", "MOMO"));

        Order saved = repo.findById(orderId).orElseThrow();
        assertThat(saved.paymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(publisher.paid).isEmpty();
    }

    @Test
    void skipsMalformedOrderId() {
        InMemoryOrderRepo repo = new InMemoryOrderRepo();
        RecordingPublisher publisher = new RecordingPublisher();
        PaymentCompletedListener listener = new PaymentCompletedListener(repo, publisher, objectMapper);

        listener.onPaymentCompleted("{\"orderId\":\"not-a-uuid\",\"status\":\"COMPLETED\"}");

        assertThat(publisher.paid).isEmpty();
    }

    private static Order pendingOrder(UUID orderId) {
        Address addr = new Address("123 Main St", "ward", "district", "city");
        OrderItem item = new OrderItem(UUID.randomUUID().toString(), "SKU-1", "seller-1", "Product", 1,
                new Money(new BigDecimal("100")), "img");
        SubOrder sub = new SubOrder("seller-1", List.of(item));
        return new Order(orderId, "VNS-TEST-00001", "buyer-1", addr, List.of(sub),
                new Money(new BigDecimal("100")), Money.ZERO, Money.ZERO,
                "PAYPAL", PaymentStatus.PENDING, "idem-" + orderId);
    }

    private String eventJson(UUID orderId, String status, String provider) {
        return String.format(
                "{\"orderId\":\"%s\",\"status\":\"%s\",\"provider\":\"%s\",\"transactionRef\":\"ref-1\"}",
                orderId, status, provider);
    }

    private static final class InMemoryOrderRepo implements OrderRepositoryPort {
        private final Map<UUID, Order> rows = new HashMap<>();
        int saveCount;

        @Override
        public Order save(Order order) {
            saveCount++;
            rows.put(order.id(), order);
            return order;
        }

        @Override
        public Optional<Order> findById(UUID orderId) {
            return Optional.ofNullable(rows.get(orderId));
        }

        @Override public Optional<Order> findByOrderNumber(String orderNumber) { return Optional.empty(); }
        @Override public Optional<Order> findByIdempotencyKey(String idempotencyKey) { return Optional.empty(); }
        @Override public List<Order> findByBuyerId(String buyerId) { return List.of(); }
        @Override public Optional<Order> findBySubOrderId(Long subOrderId) { return Optional.empty(); }
        @Override public Optional<String> findOrderIdBySubOrderId(Long subOrderId) { return Optional.empty(); }
        @Override public List<Order> findBySellerIdAndFulfillmentStatus(String sellerId, FulfillmentStatus status) { return List.of(); }
    }

    private static final class RecordingPublisher implements OrderEventPublisherPort {
        final List<Order> paid = new ArrayList<>();
        @Override public void publishOrderCreated(Order order) {}
        @Override public void publishOrderUpdated(Order order) {}
        @Override public void publishOrderPaid(Order order) { paid.add(order); }
    }
}
