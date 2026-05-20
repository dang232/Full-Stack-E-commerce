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
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Locks the pt15 ReturnAuthorization gate at the use-case boundary. Day-simulation
 * covers the HTTP 403 path; this test exercises each branch of
 * ReturnAuthorization.requireSellerOwnsReturn — including "no return saved on
 * mismatch", which the HTTP test can't see.
 *
 * RejectReturnUseCase is structurally identical (same gate, single state
 * transition); CompleteReturnUseCase adds a refund-port side-effect after
 * the gate. Covering Approve here is the highest-value of the three because
 * it sets the contract for the shared helper.
 */
class ApproveReturnUseCaseTest {
    private static final String SELLER_OWNER = "seller-1";
    private static final String SELLER_ATTACKER = "seller-2";
    private static final Money TEN_THOUSAND = new Money(BigDecimal.valueOf(10_000), "VND");

    private final TestFakes.FakeReturnRepository returns = new TestFakes.FakeReturnRepository();
    private final TestFakes.FakeOrderRepository orders = new TestFakes.FakeOrderRepository();
    private final ApproveReturnUseCase useCase = new ApproveReturnUseCase(returns, orders);

    @Test
    void approveTransitionsReturnWhenSellerOwnsSubOrder() {
        UUID orderId = UUID.randomUUID();
        UUID returnId = UUID.randomUUID();
        Long subOrderId = 100L;
        orders.save(orderWith(orderId, subOrderId, SELLER_OWNER));
        returns.save(new Return(returnId, orderId.toString(), subOrderId, "buyer-1", "broken"));

        Return approved = useCase.approve(returnId, SELLER_OWNER);

        assertThat(approved.status()).isEqualTo(ReturnStatus.APPROVED);
        // Saved row reflects the new status — controller-side cache invalidation
        // depends on the persisted state matching the returned domain object.
        assertThat(returns.findById(returnId).orElseThrow().status())
                .isEqualTo(ReturnStatus.APPROVED);
    }

    @Test
    void approveByWrongSellerThrowsAccessDeniedAndDoesNotMutateReturn() {
        UUID orderId = UUID.randomUUID();
        UUID returnId = UUID.randomUUID();
        Long subOrderId = 100L;
        orders.save(orderWith(orderId, subOrderId, SELLER_OWNER));
        returns.save(new Return(returnId, orderId.toString(), subOrderId, "buyer-1", "broken"));

        assertThatThrownBy(() -> useCase.approve(returnId, SELLER_ATTACKER))
                .isInstanceOf(OrderAccessDeniedException.class);
        // Return stays REQUESTED — the gate fires before approve() mutates state.
        // Pt15 day-simulation can't assert this; only a unit test can.
        assertThat(returns.findById(returnId).orElseThrow().status())
                .isEqualTo(ReturnStatus.REQUESTED);
    }

    @Test
    void approveRejectsBlankSellerId() {
        UUID orderId = UUID.randomUUID();
        UUID returnId = UUID.randomUUID();
        Long subOrderId = 100L;
        orders.save(orderWith(orderId, subOrderId, SELLER_OWNER));
        returns.save(new Return(returnId, orderId.toString(), subOrderId, "buyer-1", "broken"));

        assertThatThrownBy(() -> useCase.approve(returnId, "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sellerId");
        assertThat(returns.findById(returnId).orElseThrow().status())
                .isEqualTo(ReturnStatus.REQUESTED);
    }

    @Test
    void approveRejectsUnknownReturn() {
        assertThatThrownBy(() -> useCase.approve(UUID.randomUUID(), SELLER_OWNER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("return not found");
    }

    @Test
    void approveRejectsReturnPointingAtMissingOrder() {
        // Edge case: a Return row exists but the Order referenced by orderId
        // is gone. ReturnAuthorization should fail with IllegalArgumentException
        // ("order not found") rather than a NullPointerException — surface a
        // specific bad-state error over a generic crash.
        UUID returnId = UUID.randomUUID();
        returns.save(new Return(returnId, UUID.randomUUID().toString(), 100L, "buyer-1", "broken"));

        assertThatThrownBy(() -> useCase.approve(returnId, SELLER_OWNER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("order not found");
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
}
