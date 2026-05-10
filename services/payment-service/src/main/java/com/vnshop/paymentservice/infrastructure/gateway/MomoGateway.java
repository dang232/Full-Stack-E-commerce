package com.vnshop.paymentservice.infrastructure.gateway;

import com.vnshop.paymentservice.domain.Payment;
import com.vnshop.paymentservice.domain.PaymentStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.RoundingMode;
import java.util.Objects;

@Component
@ConditionalOnProperty(name = "payment.mode", havingValue = "live")
public class MomoGateway {
    private static final String EXTRA_DATA = "";

    private final MomoProperties properties;
    private final MomoClient momoClient;
    private final MomoSigner signer;

    public MomoGateway(MomoProperties properties, MomoClient momoClient) {
        this.properties = Objects.requireNonNull(properties, "properties is required");
        this.momoClient = Objects.requireNonNull(momoClient, "momoClient is required");
        this.signer = new MomoSigner(properties.secretKey());
        requireNonBlank(properties.partnerCode(), "MoMo partner code is required");
        requireNonBlank(properties.accessKey(), "MoMo access key is required");
        requireNonBlank(properties.redirectUrl(), "MoMo redirect URL is required");
        requireNonBlank(properties.ipnUrl(), "MoMo IPN URL is required");
    }

    public PaymentGatewayResult processPayment(Payment payment) {
        MomoCreateResponse response = createPayment(payment);
        if (response == null || response.resultCode() != 0 || response.payUrl() == null || response.payUrl().isBlank()) {
            return new PaymentGatewayResult(PaymentStatus.FAILED, payment.paymentId());
        }
        return new PaymentGatewayResult(PaymentStatus.PENDING, response.payUrl());
    }

    public MomoCreateResponse createPayment(Payment payment) {
        return momoClient.create(createRequest(payment));
    }

    public MomoCreateRequest createRequest(Payment payment) {
        String amount = toMomoAmount(payment);
        MomoCreateRequest unsignedRequest = new MomoCreateRequest(
                properties.partnerCode(),
                properties.accessKey(),
                payment.paymentId(),
                Long.parseLong(amount),
                payment.paymentId(),
                "Payment for order " + payment.orderId(),
                properties.redirectUrl(),
                properties.ipnUrl(),
                EXTRA_DATA,
                properties.requestType(),
                properties.lang(),
                null
        );
        String signature = signer.sign(MomoSigner.orderedCreateParameters(unsignedRequest));
        return new MomoCreateRequest(
                unsignedRequest.partnerCode(),
                unsignedRequest.accessKey(),
                unsignedRequest.requestId(),
                unsignedRequest.amount(),
                unsignedRequest.orderId(),
                unsignedRequest.orderInfo(),
                unsignedRequest.redirectUrl(),
                unsignedRequest.ipnUrl(),
                unsignedRequest.extraData(),
                unsignedRequest.requestType(),
                unsignedRequest.lang(),
                signature
        );
    }

    public MomoVerification verifyIpn(MomoIpnRequest request) {
        boolean validSignature = signer.verify(MomoSigner.orderedIpnParameters(request), request.signature());
        PaymentStatus status = validSignature && request.resultCode() == 0 ? PaymentStatus.COMPLETED : PaymentStatus.FAILED;
        return new MomoVerification(validSignature, status, request.orderId(), String.valueOf(request.transId()), request.resultCode(), request.message());
    }

    public MomoQueryDrRequest queryDrRequest(String paymentId) {
        MomoQueryDrRequest unsignedRequest = new MomoQueryDrRequest(
                properties.partnerCode(),
                properties.accessKey(),
                paymentId + "-query",
                paymentId,
                properties.lang(),
                null
        );
        String signature = signer.sign(MomoSigner.orderedQueryDrParameters(unsignedRequest));
        return new MomoQueryDrRequest(
                unsignedRequest.partnerCode(),
                unsignedRequest.accessKey(),
                unsignedRequest.requestId(),
                unsignedRequest.orderId(),
                unsignedRequest.lang(),
                signature
        );
    }

    public PaymentStatus getStatus(String paymentId) {
        MomoQueryDrResponse response = momoClient.query(queryDrRequest(paymentId));
        return response != null && response.resultCode() == 0 ? PaymentStatus.COMPLETED : PaymentStatus.PENDING;
    }

    private String toMomoAmount(Payment payment) {
        return payment.amount().setScale(0, RoundingMode.UNNECESSARY).toPlainString();
    }

    private static void requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    public record PaymentGatewayResult(PaymentStatus status, String transactionRef) {
    }

    public record MomoVerification(
            boolean validSignature,
            PaymentStatus status,
            String paymentId,
            String transactionNo,
            int resultCode,
            String message
    ) {
    }
}
