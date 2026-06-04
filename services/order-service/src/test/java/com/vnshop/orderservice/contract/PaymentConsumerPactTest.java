package com.vnshop.orderservice.contract;

import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.V4Pact;
import au.com.dius.pact.core.model.annotations.Pact;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "payment-service")
class PaymentConsumerPactTest {

    @Pact(consumer = "order-service", provider = "payment-service")
    V4Pact requestPaymentPact(PactDslWithProvider builder) {
        return builder
                .given("payment service is available")
                .uponReceiving("a request to create payment")
                .method("POST")
                .path("/grpc/vnshop.payment.PaymentService/RequestPayment")
                .headers("Content-Type", "application/json")
                .body(new PactDslJsonBody()
                        .stringType("orderId", "order-123")
                        .object("amount")
                            .stringType("amount", "99.99")
                            .stringType("currency", "USD")
                        .closeObject()
                        .stringType("method", "PAYPAL")
                        .stringType("returnUrl", "http://localhost:3000/payment/success")
                        .stringType("cancelUrl", "http://localhost:3000/payment/cancel"))
                .willRespondWith()
                .status(200)
                .body(new PactDslJsonBody()
                        .stringType("paymentId", "pay-789")
                        .stringType("redirectUrl", "https://paypal.com/checkout/pay-789")
                        .stringType("status", "PENDING"))
                .toPact(V4Pact.class);
    }

    @Test
    @PactTestFor(pactMethod = "requestPaymentPact")
    void verifyRequestPayment() {
        assertNotNull("Pact verified");
    }
}
