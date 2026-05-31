package com.vnshop.orderservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vnshop.orderservice.domain.Address;
import com.vnshop.orderservice.domain.FulfillmentStatus;
import com.vnshop.orderservice.domain.Money;
import com.vnshop.orderservice.domain.Order;
import com.vnshop.orderservice.domain.OrderItem;
import com.vnshop.orderservice.domain.PaymentStatus;
import com.vnshop.orderservice.domain.SubOrder;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Locks the pt14 buyer cross-check on ViewOrderUseCase.viewForBuyer. Same
 * pattern as GetPaymentStatusUseCase: two methods, one trusted internal
 * (view) and one HTTP-facing (viewForBuyer). The boundary between them is
 * audit-relevant — pt14 specifically introduced viewForBuyer because the
 * controller had been routing through view() with no ownership check.
 */
class ViewOrderUseCaseTest {
    private static final Money TEN_THOUSAND = new Money(BigDecimal.valueOf(10_000), "VND");

    private final TestFakes.FakeOrderRepository repository = new TestFakes.FakeOrderRepository();
    private final ViewOrderUseCase useCase = new ViewOrderUseCase(repository);

    @Test
    void viewForBuyerReturnsOrderWhenBuyerMatches() {
        UUID orderId = UUID.randomUUID();
        repository.save(orderWith(orderId, "buyer-1"));

        Order order = useCase.viewForBuyer(orderId, "buyer-1");

        assertThat(order.buyerId()).isEqualTo("buyer-1");
    }

    @Test
    void viewForBuyerThrowsAccessDeniedForForeignBuyer() {
        // Pt14 audit: pre-fix, any authenticated buyer could read any other
        // buyer's full order — shipping address, items, prices, status — by
        // guessing the orderId UUID.
        UUID orderId = UUID.randomUUID();
        repository.save(orderWith(orderId, "buyer-1"));

        assertThatThrownBy(() -> useCase.viewForBuyer(orderId, "buyer-2"))
                .isInstanceOf(OrderAccessDeniedException.class);
    }

    @Test
    void viewForBuyerThrowsForUnknownOrder() {
        assertThatThrownBy(() -> useCase.viewForBuyer(UUID.randomUUID(), "buyer-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("order not found");
    }

    @Test
    void viewForBuyerRejectsNullBuyerId() {
        UUID orderId = UUID.randomUUID();
        repository.save(orderWith(orderId, "buyer-1"));

        assertThatThrownBy(() -> useCase.viewForBuyer(orderId, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("buyerId");
    }

    @Test
    void viewSkipsBuyerCheckForInternalCallers() {
        // Trusted internal path used by other use cases that have already
        // authorised the buyer (e.g. CancelOrderUseCase, payment-side
        // adapters). Pinning this so a future refactor doesn't accidentally
        // route the HTTP controller through view() and re-introduce the IDOR.
        UUID orderId = UUID.randomUUID();
        repository.save(orderWith(orderId, "buyer-1"));

        Order order = useCase.view(orderId);

        assertThat(order.id()).isEqualTo(orderId);
    }

    private static Order orderWith(UUID orderId, String buyerId) {
        OrderItem item = new OrderItem("product-1", "P-1", "seller-1", "Phone", 1, TEN_THOUSAND, null);
        SubOrder subOrder = new SubOrder(100L, "seller-1", List.of(item),
                FulfillmentStatus.SHIPPED, Money.ZERO, "STANDARD", "GHN", "TRK-1");
        Address shippingAddress = new Address("123 Day Street", "Ward 1", "District 1", "HCMC");
        return new Order(orderId, "ORD-1", buyerId, shippingAddress, List.of(subOrder),
                TEN_THOUSAND, Money.ZERO, Money.ZERO,
                "COD", PaymentStatus.PENDING, "idem-1");
    }
}
