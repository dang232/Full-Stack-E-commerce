package com.vnshop.paymentservice.infrastructure.gateway;

import com.vnshop.paymentservice.domain.Payment;
import com.vnshop.paymentservice.domain.PaymentMethod;
import com.vnshop.paymentservice.domain.PaymentStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MomoGatewayTest {
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
    void createsSignedPaymentRequest() {
        Payment payment = new Payment(paymentId(1), "ORDER-1", "BUYER-1", new BigDecimal("120000.00"), PaymentMethod.MOMO, PaymentStatus.PENDING, null, Instant.parse("2026-05-10T00:00:00Z"));
        CapturingMomoClient client = new CapturingMomoClient(new MomoCreateResponse("MOMOTEST", paymentId(1).toString(), paymentId(1).toString(), 120000, 1777777777777L, "Success", 0, "https://pay.example/PAY-1", "momo://pay/PAY-1", "qr"));
        MomoGateway gateway = new MomoGateway(PROPERTIES, client);

        MomoCreateResponse response = gateway.createPayment(payment);

        assertThat(response.payUrl()).isEqualTo("https://pay.example/PAY-1");
        assertThat(response.deeplink()).isEqualTo("momo://pay/PAY-1");
        assertThat(client.createRequest)
                .extracting(MomoCreateRequest::partnerCode, MomoCreateRequest::requestId, MomoCreateRequest::orderId, MomoCreateRequest::amount, MomoCreateRequest::redirectUrl, MomoCreateRequest::ipnUrl, MomoCreateRequest::requestType, MomoCreateRequest::lang)
                .containsExactly("MOMOTEST", paymentId(1).toString(), paymentId(1).toString(), 120000L, "https://shop.example/payment/momo/return", "https://shop.example/payment/momo/ipn", "captureWallet", "vi");
        assertThat(client.createRequest.signature()).hasSize(64);
    }

    @Test
    void signsCanonicalRawSignatureDeterministically() {
        MomoSigner signer = new MomoSigner("secret-key");
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("accessKey", "access-key");
        parameters.put("amount", "120000");
        parameters.put("extraData", "");
        parameters.put("ipnUrl", "https://shop.example/payment/momo/ipn");
        parameters.put("orderId", "PAY-1");
        parameters.put("orderInfo", "Payment for order ORDER-1");
        parameters.put("partnerCode", "MOMOTEST");
        parameters.put("redirectUrl", "https://shop.example/payment/momo/return");
        parameters.put("requestId", "PAY-1");
        parameters.put("requestType", "captureWallet");

        assertThat(signer.rawSignature(parameters))
                .isEqualTo("accessKey=access-key&amount=120000&extraData=&ipnUrl=https://shop.example/payment/momo/ipn&orderId=PAY-1&orderInfo=Payment for order ORDER-1&partnerCode=MOMOTEST&redirectUrl=https://shop.example/payment/momo/return&requestId=PAY-1&requestType=captureWallet");
        assertThat(signer.sign(parameters)).isEqualTo("f880f9ed2db747d3a0316c4abf4334d0d7d1bd780e62185262bfe80bf0e4d899");
    }

    @Test
    void parsesVerifiedIpnAsCompletedAndRejectsTampering() {
        MomoGateway gateway = new MomoGateway(PROPERTIES, new CapturingMomoClient(null));
        MomoIpnRequest verifiedRequest = ipn(paymentId(2).toString(), 2812345678L, 0, 120000L);
        MomoIpnRequest tamperedRequest = new MomoIpnRequest(verifiedRequest.partnerCode(), verifiedRequest.accessKey(), verifiedRequest.requestId(), 1L, verifiedRequest.orderId(), verifiedRequest.orderInfo(), verifiedRequest.orderType(), verifiedRequest.transId(), verifiedRequest.resultCode(), verifiedRequest.message(), verifiedRequest.payType(), verifiedRequest.responseTime(), verifiedRequest.extraData(), verifiedRequest.signature());

        MomoGateway.MomoVerification verification = gateway.verifyIpn(verifiedRequest);
        MomoGateway.MomoVerification tampered = gateway.verifyIpn(tamperedRequest);

        assertThat(verification.validSignature()).isTrue();
        assertThat(verification.status()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(verification.paymentId()).isEqualTo(paymentId(2).toString());
        assertThat(verification.transactionNo()).isEqualTo("2812345678");
        assertThat(tampered.validSignature()).isFalse();
        assertThat(tampered.status()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void createsSignedQueryDrFallbackRequest() {
        CapturingMomoClient client = new CapturingMomoClient(null);
        MomoGateway gateway = new MomoGateway(PROPERTIES, client);

        MomoQueryDrRequest request = gateway.queryDrRequest("PAY-3");

        assertThat(request.partnerCode()).isEqualTo("MOMOTEST");
        assertThat(request.requestId()).isEqualTo("PAY-3-query");
        assertThat(request.orderId()).isEqualTo("PAY-3");
        assertThat(request.lang()).isEqualTo("vi");
        assertThat(request.signature()).hasSize(64);
    }

    static MomoIpnRequest ipn(String paymentId, long transactionNo, int resultCode, long amount) {
        MomoIpnRequest unsigned = new MomoIpnRequest("MOMOTEST", "access-key", paymentId, amount, paymentId, "Payment for order ORDER-1", "momo_wallet", transactionNo, resultCode, "Successful.", "qr", 1777777777777L, "", null);
        String signature = new MomoSigner(PROPERTIES.secretKey()).sign(MomoSigner.orderedIpnParameters(unsigned));
        return new MomoIpnRequest(unsigned.partnerCode(), unsigned.accessKey(), unsigned.requestId(), unsigned.amount(), unsigned.orderId(), unsigned.orderInfo(), unsigned.orderType(), unsigned.transId(), unsigned.resultCode(), unsigned.message(), unsigned.payType(), unsigned.responseTime(), unsigned.extraData(), signature);
    }

    static UUID paymentId(int value) {
        return UUID.fromString("00000000-0000-0000-0000-00000000000" + value);
    }

    private static final class CapturingMomoClient implements MomoClient {
        private final MomoCreateResponse createResponse;
        private MomoCreateRequest createRequest;

        private CapturingMomoClient(MomoCreateResponse createResponse) {
            this.createResponse = createResponse;
        }

        @Override
        public MomoCreateResponse create(MomoCreateRequest request) {
            this.createRequest = request;
            return createResponse;
        }

        @Override
        public MomoQueryDrResponse query(MomoQueryDrRequest request) {
            return new MomoQueryDrResponse("MOMOTEST", request.requestId(), request.orderId(), 120000L, 2812345678L, 0, "Success", 1777777777777L);
        }
    }
}
