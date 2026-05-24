package com.vnshop.paymentservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vnshop.paymentservice.domain.Payment;
import com.vnshop.paymentservice.domain.PaymentMethod;
import com.vnshop.paymentservice.domain.PaymentStatus;
import com.vnshop.paymentservice.domain.port.out.PaymentRepositoryPort;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Locks the pt14 buyer cross-check on GetPaymentStatusUseCase.getByOrderIdForBuyer.
 * The HTTP path must reject foreign buyers; the gRPC/service-to-service path
 * (getByOrderId) intentionally has no buyer check — that distinction is the
 * audit-relevant invariant and this test pins it.
 */
class GetPaymentStatusUseCaseTest {
    private final FakePaymentRepository repository = new FakePaymentRepository();
    private final GetPaymentStatusUseCase useCase = new GetPaymentStatusUseCase(repository);

    @Test
    void getByOrderIdForBuyerReturnsPaymentWhenBuyerMatches() {
        repository.save(payment("order-1", "buyer-1"));

        Payment payment = useCase.getByOrderIdForBuyer("order-1", "buyer-1");

        assertThat(payment.buyerId()).isEqualTo("buyer-1");
    }

    @Test
    void getByOrderIdForBuyerThrowsAccessDeniedForForeignBuyer() {
        // Pt14 audit: pre-fix, any authenticated buyer could read any other
        // buyer's payment status (and existence-check the order) by guessing
        // the orderId UUID. The use case throws OrderAccessDeniedException
        // mapped to HTTP 403 by the controller advice.
        repository.save(payment("order-1", "buyer-1"));

        assertThatThrownBy(() -> useCase.getByOrderIdForBuyer("order-1", "buyer-2"))
                .isInstanceOf(OrderAccessDeniedException.class);
    }

    @Test
    void getByOrderIdForBuyerCollapsesUnknownOrderIntoSameMessageAsWrongBuyer() {
        // Pt39 audit: pre-fix, "order doesn't exist" raised IAE/400 while
        // "exists but not yours" raised OAD/403 with a body that embedded
        // both buyerId and orderId. A malicious caller could distinguish
        // the two cases just from status code, AND extract owner inference
        // from the message body. Collapsed into a single OAD with a
        // constant message so the response is identical regardless of
        // which condition tripped.
        repository.save(payment("order-1", "buyer-1"));

        assertThatThrownBy(() -> useCase.getByOrderIdForBuyer("missing-order", "buyer-1"))
                .isInstanceOf(OrderAccessDeniedException.class)
                .hasMessage("not authorized to read this payment");

        assertThatThrownBy(() -> useCase.getByOrderIdForBuyer("order-1", "buyer-2"))
                .isInstanceOf(OrderAccessDeniedException.class)
                .hasMessage("not authorized to read this payment");
    }

    @Test
    void getByOrderIdForBuyerDoesNotLeakBuyerIdOrOrderIdInMessage() {
        repository.save(payment("order-1", "buyer-1"));

        assertThatThrownBy(() ->
                        useCase.getByOrderIdForBuyer("order-1", "guess-target-buyer-xyz"))
                .hasMessageNotContaining("guess-target-buyer-xyz")
                .hasMessageNotContaining("order-1");
    }

    @Test
    void getByOrderIdSkipsBuyerCheckForServiceToServiceUse() {
        // gRPC server uses this path because the caller is order-service
        // polling for status — trusted, no JWT in scope. The audit gate is
        // explicitly NOT here; it's in getByOrderIdForBuyer. This test pins
        // that boundary so a future refactor can't accidentally tighten the
        // gRPC path and break trusted readers, OR loosen the HTTP path by
        // routing through this method.
        repository.save(payment("order-1", "buyer-1"));

        Payment payment = useCase.getByOrderId("order-1");

        assertThat(payment.orderId()).isEqualTo("order-1");
    }

    private static Payment payment(String orderId, String buyerId) {
        return new Payment(
                UUID.randomUUID(),
                orderId,
                buyerId,
                BigDecimal.valueOf(10_000),
                PaymentMethod.COD,
                PaymentStatus.PENDING,
                null,
                Instant.now());
    }

    private static final class FakePaymentRepository implements PaymentRepositoryPort {
        private final Map<String, Payment> byOrderId = new HashMap<>();
        private final Map<UUID, Payment> byPaymentId = new HashMap<>();

        @Override
        public Payment save(Payment payment) {
            byOrderId.put(payment.orderId(), payment);
            byPaymentId.put(payment.paymentId(), payment);
            return payment;
        }

        @Override
        public Optional<Payment> findById(UUID paymentId) {
            return Optional.ofNullable(byPaymentId.get(paymentId));
        }

        @Override
        public Optional<Payment> findByOrderId(String orderId) {
            return Optional.ofNullable(byOrderId.get(orderId));
        }

        @Override
        public List<Payment> findByStatus(PaymentStatus status) {
            return List.of();
        }
    }
}
