package com.vnshop.paymentservice.infrastructure.gateway;

import com.vnshop.paymentservice.domain.JournalEntry;
import com.vnshop.paymentservice.domain.LedgerEntry;
import com.vnshop.paymentservice.domain.Payment;
import com.vnshop.paymentservice.domain.PaymentStatus;
import com.vnshop.paymentservice.domain.port.out.LedgerRepositoryPort;
import com.vnshop.paymentservice.application.ledger.LedgerService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class VnpayGatewayTest {
    private static final VnpayProperties PROPERTIES = new VnpayProperties(
            "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html",
            "TESTTMN",
            "secret-key",
            "https://shop.example/payment/vnpay/return",
            "https://shop.example/payment/vnpay/ipn",
            "2.1.0",
            "pay",
            "other",
            "vn",
            "VND",
            15
    );

    @Test
    void createsSignedPaymentUrlWithSortedCanonicalParameters() {
        Payment payment = new Payment("PAY-1", "ORDER-1", "BUYER-1", new BigDecimal("120000.00"), Payment.Method.VNPAY, PaymentStatus.PENDING, null, Instant.parse("2026-05-10T00:00:00Z"));
        VnpayGateway gateway = gateway();

        String paymentUrl = gateway.createPaymentUrl(payment, "203.0.113.10");
        Map<String, String> parameters = queryParameters(paymentUrl);

        assertThat(paymentUrl).startsWith("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?");
        assertThat(parameters)
                .containsEntry("vnp_TmnCode", "TESTTMN")
                .containsEntry("vnp_TxnRef", "PAY-1")
                .containsEntry("vnp_Amount", "12000000")
                .containsEntry("vnp_ReturnUrl", "https://shop.example/payment/vnpay/return")
                .containsEntry("vnp_IpnUrl", "https://shop.example/payment/vnpay/ipn")
                .containsEntry("vnp_CreateDate", "20260510120000")
                .containsEntry("vnp_ExpireDate", "20260510121500");
        assertThat(parameters.get("vnp_SecureHash")).hasSize(128);
        assertThat(gateway.verify(parameters).validSignature()).isTrue();
    }

    @Test
    void rejectsTamperedSignature() {
        Payment payment = new Payment("PAY-2", "ORDER-2", "BUYER-2", new BigDecimal("1000.00"), Payment.Method.VNPAY, PaymentStatus.PENDING, null, Instant.parse("2026-05-10T00:00:00Z"));
        VnpayGateway gateway = gateway();
        Map<String, String> parameters = queryParameters(gateway.createPaymentUrl(payment, "203.0.113.10"));
        parameters.put("vnp_Amount", "200000");

        VnpayGateway.VnpayVerification verification = gateway.verify(parameters);

        assertThat(verification.validSignature()).isFalse();
        assertThat(verification.status()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void parsesVerifiedCallbackAsCompleted() {
        VnpayGateway gateway = gateway();
        Map<String, String> parameters = queryParameters(gateway.createPaymentUrl(new Payment("PAY-3", "ORDER-3", "BUYER-3", new BigDecimal("50000.00"), Payment.Method.VNPAY, PaymentStatus.PENDING, null, Instant.parse("2026-05-10T00:00:00Z")), "203.0.113.10"));
        parameters.put("vnp_ResponseCode", "00");
        parameters.put("vnp_TransactionStatus", "00");
        parameters.put("vnp_TransactionNo", "14123456");
        parameters.put("vnp_SecureHash", new VnpaySigner(PROPERTIES.hashSecret()).sign(parameters));

        VnpayGateway.VnpayVerification verification = gateway.verify(parameters);

        assertThat(verification.validSignature()).isTrue();
        assertThat(verification.status()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(verification.paymentId()).isEqualTo("PAY-3");
        assertThat(verification.transactionNo()).isEqualTo("14123456");
        assertThat(verification.responseCode()).isEqualTo("00");
        assertThat(verification.transactionStatus()).isEqualTo("00");
    }

    private VnpayGateway gateway() {
        Clock clock = Clock.fixed(Instant.parse("2026-05-10T05:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
        return new VnpayGateway(PROPERTIES, new LedgerService(new NoopLedgerRepository()), clock);
    }

    private Map<String, String> queryParameters(String url) {
        String query = url.substring(url.indexOf('?') + 1);
        return Arrays.stream(query.split("&"))
                .map(pair -> pair.split("=", 2))
                .collect(Collectors.toMap(
                        pair -> decode(pair[0]),
                        pair -> pair.length == 2 ? decode(pair[1]) : ""));
    }

    private String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static final class NoopLedgerRepository implements LedgerRepositoryPort {
        @Override
        public List<LedgerEntry> append(JournalEntry journalEntry) {
            return List.of();
        }

        @Override
        public List<LedgerEntry> findByOrderId(String orderId) {
            return List.of();
        }

        @Override
        public List<LedgerEntry> findByJournalId(String journalId) {
            return List.of();
        }
    }
}
