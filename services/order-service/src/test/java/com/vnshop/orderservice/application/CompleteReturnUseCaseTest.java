package com.vnshop.orderservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vnshop.orderservice.domain.Address;
import com.vnshop.orderservice.domain.FulfillmentStatus;
import com.vnshop.orderservice.domain.Money;
import com.vnshop.orderservice.domain.Order;
import com.vnshop.orderservice.domain.OrderItem;
import com.vnshop.orderservice.domain.PaymentStatus;
import com.vnshop.orderservice.domain.Return;
import com.vnshop.orderservice.domain.ReturnStatus;
import com.vnshop.orderservice.domain.SubOrder;
import com.vnshop.orderservice.domain.port.out.OrderRepositoryPort;
import com.vnshop.orderservice.domain.port.out.RefundRequestPort;
import com.vnshop.orderservice.domain.port.out.ReturnRepositoryPort;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * CompleteReturnUseCase is the highest-stakes return endpoint — it triggers
 * a real refund via RefundRequestPort. The crucial assertion this unit test
 * makes (and day-simulation cannot) is: on wrong-seller / blank-seller /
 * unknown-return, the refund port is NOT called. Any drift in the gate
 * ordering or in the use case body that allowed `requestRefund` to fire on
 * an unauthorized request would be a critical leak.
 */
class CompleteReturnUseCaseTest {
    private static final String SELLER_OWNER = "seller-1";
    private static final String SELLER_ATTACKER = "seller-2";
    private static final Money TEN_THOUSAND = new Money(BigDecimal.valueOf(10_000), "VND");

    private final FakeReturnRepository returns = new FakeReturnRepository();
    private final FakeOrderRepository orders = new FakeOrderRepository();
    private final RecordingRefundPort refunds = new RecordingRefundPort();
    private final CompleteReturnUseCase useCase = new CompleteReturnUseCase(returns, orders, refunds);

    @Test
    void completeTransitionsReturnAndRequestsRefundForOwnerSeller() {
        UUID orderId = UUID.randomUUID();
        UUID returnId = UUID.randomUUID();
        Long subOrderId = 100L;
        orders.save(orderWith(orderId, subOrderId, SELLER_OWNER));
        returns.save(approvedReturn(returnId, orderId, subOrderId, "buyer-1"));

        Return completed = useCase.complete(returnId, SELLER_OWNER);

        assertThat(completed.status()).isEqualTo(ReturnStatus.COMPLETED);
        assertThat(refunds.calls).hasSize(1);
        assertThat(refunds.calls.get(0).amount).isEqualTo(TEN_THOUSAND);
        assertThat(refunds.calls.get(0).returnId).isEqualTo(returnId);
    }

    @Test
    void completeByWrongSellerDoesNotTriggerRefund() {
        // Highest-stakes assertion in the suite. Pre-pt15 a wrong-seller call
        // would issue a real refund on someone else's return. This test asserts
        // the refund port stays untouched — controller-level 403 alone isn't
        // enough; the use case has to fail before reaching requestRefund.
        UUID orderId = UUID.randomUUID();
        UUID returnId = UUID.randomUUID();
        Long subOrderId = 100L;
        orders.save(orderWith(orderId, subOrderId, SELLER_OWNER));
        returns.save(approvedReturn(returnId, orderId, subOrderId, "buyer-1"));

        assertThatThrownBy(() -> useCase.complete(returnId, SELLER_ATTACKER))
                .isInstanceOf(OrderAccessDeniedException.class);
        assertThat(refunds.calls).isEmpty();
        // Return stays in pre-complete state — gate ran before complete() mutated it.
        assertThat(returns.findById(returnId).orElseThrow().status())
                .isEqualTo(ReturnStatus.APPROVED);
    }

    @Test
    void completeRejectsBlankSellerIdWithoutRefund() {
        UUID orderId = UUID.randomUUID();
        UUID returnId = UUID.randomUUID();
        Long subOrderId = 100L;
        orders.save(orderWith(orderId, subOrderId, SELLER_OWNER));
        returns.save(approvedReturn(returnId, orderId, subOrderId, "buyer-1"));

        assertThatThrownBy(() -> useCase.complete(returnId, "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sellerId");
        assertThat(refunds.calls).isEmpty();
    }

    @Test
    void completeRejectsUnknownReturnWithoutRefund() {
        assertThatThrownBy(() -> useCase.complete(UUID.randomUUID(), SELLER_OWNER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("return not found");
        assertThat(refunds.calls).isEmpty();
    }

    private static Return approvedReturn(UUID returnId, UUID orderId, Long subOrderId, String buyerId) {
        // Returns must be APPROVED before they can complete — match the real
        // state machine so the test exercises the same path production hits.
        return new Return(returnId, orderId.toString(), subOrderId, buyerId, "broken",
                ReturnStatus.APPROVED, Instant.now(), null);
    }

    private static Order orderWith(UUID orderId, Long subOrderId, String sellerId) {
        OrderItem item = new OrderItem("product-1", "P-1", sellerId, "Phone", 1, TEN_THOUSAND, null);
        SubOrder subOrder = new SubOrder(subOrderId, sellerId, List.of(item),
                FulfillmentStatus.SHIPPED, Money.ZERO, "STANDARD", "GHN", "TRK-1");
        Address shippingAddress = new Address("123 Day Street", "Ward 1", "District 1", "HCMC");
        return new Order(orderId, "ORD-1", "buyer-1", shippingAddress, List.of(subOrder),
                TEN_THOUSAND, Money.ZERO, Money.ZERO,
                "COD", PaymentStatus.COMPLETED, "idem-1");
    }

    private static final class RecordingRefundPort implements RefundRequestPort {
        record Call(UUID returnId, Money amount) {}
        final List<Call> calls = new ArrayList<>();

        @Override
        public void requestRefund(Return orderReturn, Money amount) {
            calls.add(new Call(orderReturn.returnId(), amount));
        }
    }

    private static final class FakeOrderRepository implements OrderRepositoryPort {
        private final Map<UUID, Order> orders = new HashMap<>();

        @Override
        public Order save(Order order) {
            orders.put(order.id(), order);
            return order;
        }

        @Override
        public Optional<Order> findById(UUID orderId) {
            return Optional.ofNullable(orders.get(orderId));
        }

        @Override public Optional<Order> findByOrderNumber(String orderNumber) { return Optional.empty(); }
        @Override public Optional<Order> findByIdempotencyKey(String idempotencyKey) { return Optional.empty(); }
        @Override public List<Order> findByBuyerId(String buyerId) { return List.of(); }
        @Override public Optional<Order> findBySubOrderId(Long subOrderId) { return Optional.empty(); }
        @Override public Optional<String> findOrderIdBySubOrderId(Long subOrderId) { return Optional.empty(); }
        @Override public List<Order> findBySellerIdAndFulfillmentStatus(String sellerId, FulfillmentStatus status) { return List.of(); }
    }

    private static final class FakeReturnRepository implements ReturnRepositoryPort {
        private final Map<UUID, Return> returns = new HashMap<>();

        @Override
        public Return save(Return r) {
            returns.put(r.returnId(), r);
            return r;
        }

        @Override
        public Optional<Return> findById(UUID returnId) {
            return Optional.ofNullable(returns.get(returnId));
        }

        @Override
        public List<Return> findByBuyerId(String buyerId) {
            return List.of();
        }
    }
}
