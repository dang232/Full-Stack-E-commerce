package com.vnshop.paymentservice.infrastructure.gateway;

import com.vnshop.paymentservice.domain.LedgerEntry;
import com.vnshop.paymentservice.domain.Payment;
import com.vnshop.paymentservice.domain.PaymentStatus;
import com.vnshop.paymentservice.domain.port.out.LedgerRepositoryPort;
import com.vnshop.paymentservice.domain.port.out.PaymentRepositoryPort;
import com.vnshop.paymentservice.infrastructure.ledger.LedgerService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class MomoCallbackServiceTest {
    private static final MomoProperties PROPERTIES = new MomoProperties(
            "https://test-payment.momo.vn/v2/gateway/api/create",
            "https://test-payment.momo.vn/v2/gateway/api/query",
            "MOMOTEST",
            "access-key",
            "secret-key",
            "https://shop.example/payment/momo/return",
            "https://shop.example/payment/momo/ipn",
            "captureWallet",
            "vi"
    );

    @Test
    void ipnUpdatesPendingMomoPaymentAndRecordsLedgerAfterValidSignature() {
        CapturingPaymentRepository repository = new CapturingPaymentRepository(payment(PaymentStatus.PENDING, null));
        CapturingLedgerRepository ledgerRepository = new CapturingLedgerRepository();
        MomoCallbackService service = service(repository, ledgerRepository);

        MomoCallbackService.MomoIpnResult result = service.handleIpn(MomoGatewayTest.ipn("PAY-1", 2812345678L, 0, 120000L));

        assertThat(result.resultCode()).isEqualTo(0);
        assertThat(repository.payment.status()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(repository.payment.transactionRef()).isEqualTo("2812345678");
        assertThat(repository.savedPayments).hasSize(1);
        assertThat(ledgerRepository.savedEntries).hasSize(2);
    }

    @Test
    void ipnRejectsTamperedSignatureWithoutStateChange() {
        CapturingPaymentRepository repository = new CapturingPaymentRepository(payment(PaymentStatus.PENDING, null));
        CapturingLedgerRepository ledgerRepository = new CapturingLedgerRepository();
        MomoCallbackService service = service(repository, ledgerRepository);
        MomoIpnRequest signed = MomoGatewayTest.ipn("PAY-1", 2812345678L, 0, 120000L);
        MomoIpnRequest tampered = new MomoIpnRequest(signed.partnerCode(), signed.accessKey(), signed.requestId(), 1L, signed.orderId(), signed.orderInfo(), signed.orderType(), signed.transId(), signed.resultCode(), signed.message(), signed.payType(), signed.responseTime(), signed.extraData(), signed.signature());

        MomoCallbackService.MomoIpnResult result = service.handleIpn(tampered);

        assertThat(result.resultCode()).isEqualTo(97);
        assertThat(repository.payment.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(repository.savedPayments).isEmpty();
        assertThat(ledgerRepository.savedEntries).isEmpty();
    }

    @Test
    void gatewayReturnVerificationDoesNotMutatePayment() {
        CapturingPaymentRepository repository = new CapturingPaymentRepository(payment(PaymentStatus.PENDING, null));
        MomoGateway gateway = new MomoGateway(PROPERTIES, new CapturingMomoClient());

        MomoGateway.MomoVerification verification = gateway.verifyIpn(MomoGatewayTest.ipn("PAY-1", 2812345678L, 0, 120000L));

        assertThat(verification.validSignature()).isTrue();
        assertThat(verification.status()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(repository.payment.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(repository.savedPayments).isEmpty();
    }

    private static MomoCallbackService service(CapturingPaymentRepository repository, CapturingLedgerRepository ledgerRepository) {
        return new MomoCallbackService(repository, new LedgerService(ledgerRepository), new MomoGateway(PROPERTIES, new CapturingMomoClient()));
    }

    private static Payment payment(PaymentStatus status, String transactionRef) {
        return new Payment("PAY-1", "ORDER-1", "BUYER-1", new BigDecimal("120000.00"), Payment.Method.MOMO, status, transactionRef, Instant.parse("2026-05-10T09:00:00Z"));
    }

    private static final class CapturingMomoClient implements MomoClient {
        @Override
        public MomoCreateResponse create(MomoCreateRequest request) {
            return null;
        }

        @Override
        public MomoQueryDrResponse query(MomoQueryDrRequest request) {
            return null;
        }
    }

    private static final class CapturingPaymentRepository implements PaymentRepositoryPort {
        private Payment payment;
        private final List<Payment> savedPayments = new ArrayList<>();

        private CapturingPaymentRepository(Payment payment) {
            this.payment = payment;
        }

        @Override
        public Payment save(Payment payment) {
            this.payment = payment;
            savedPayments.add(payment);
            return payment;
        }

        @Override
        public Optional<Payment> findById(String paymentId) {
            return payment.paymentId().equals(paymentId) ? Optional.of(payment) : Optional.empty();
        }

        @Override
        public Optional<Payment> findByOrderId(String orderId) {
            return Optional.empty();
        }

        @Override
        public List<Payment> findByStatus(PaymentStatus status) {
            return List.of();
        }
    }

    private static final class CapturingLedgerRepository implements LedgerRepositoryPort {
        private final List<LedgerEntry> savedEntries = new ArrayList<>();

        @Override
        public LedgerEntry save(LedgerEntry ledgerEntry) {
            savedEntries.add(ledgerEntry);
            return ledgerEntry;
        }

        @Override
        public List<LedgerEntry> findByOrderId(String orderId) {
            return List.of();
        }
    }
}
