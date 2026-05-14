package com.vnshop.orderservice.infrastructure.grpc;

import com.vnshop.orderservice.domain.Money;
import com.vnshop.orderservice.domain.port.out.PaymentRequestPort;
import com.vnshop.proto.payment.PaymentRequest;
import com.vnshop.proto.payment.PaymentServiceGrpc;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class GrpcPaymentRequestAdapter implements PaymentRequestPort {

    private static final Logger log = LoggerFactory.getLogger(GrpcPaymentRequestAdapter.class);

    private final PaymentServiceGrpc.PaymentServiceBlockingStub paymentStub;

    public GrpcPaymentRequestAdapter(PaymentServiceGrpc.PaymentServiceBlockingStub paymentStub) {
        this.paymentStub = paymentStub;
    }

    @Override
    public void requestPayment(String orderId, String paymentMethod, Money amount) {
        var protoMoney = com.vnshop.proto.common.Money.newBuilder()
            .setAmount(amount.amount().toPlainString())
            .setCurrency(amount.currency())
            .build();

        var request = PaymentRequest.newBuilder()
            .setOrderId(orderId)
            .setPaymentMethod(paymentMethod)
            .setAmount(protoMoney)
            .build();

        try {
            var response = paymentStub.requestPayment(request);
            log.info("Payment requested: orderId={}, paymentId={}, status={}",
                orderId, response.getPaymentId(), response.getStatus());
        } catch (StatusRuntimeException e) {
            log.error("gRPC payment request failed: orderId={}, code={}, message={}",
                orderId, e.getStatus().getCode(), e.getStatus().getDescription(), e);
            throw new PaymentRequestFailedException("Payment request failed for order " + orderId, e);
        }
    }
}
