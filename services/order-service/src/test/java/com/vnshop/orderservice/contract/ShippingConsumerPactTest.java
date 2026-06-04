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
@PactTestFor(providerName = "shipping-service")
class ShippingConsumerPactTest {

    @Pact(consumer = "order-service", provider = "shipping-service")
    V4Pact requestShippingPact(PactDslWithProvider builder) {
        return builder
                .given("shipping service is available")
                .uponReceiving("a request to create shipment")
                .method("POST")
                .path("/grpc/vnshop.shipping.ShippingService/RequestShipping")
                .headers("Content-Type", "application/json")
                .body(new PactDslJsonBody()
                        .stringType("orderId", "order-123")
                        .object("address")
                            .stringType("line1", "123 Main St")
                            .stringType("city", "Springfield")
                            .stringType("postalCode", "62701")
                            .stringType("country", "US")
                        .closeObject()
                        .array("items")
                            .object()
                                .stringType("productId", "SKU-001")
                                .integerType("quantity", 2)
                            .closeObject()
                        .closeArray())
                .willRespondWith()
                .status(200)
                .body(new PactDslJsonBody()
                        .stringType("shipmentId", "ship-101")
                        .stringType("trackingNumber", "TRK-XYZ-123")
                        .stringType("carrier", "FEDEX"))
                .toPact(V4Pact.class);
    }

    @Test
    @PactTestFor(pactMethod = "requestShippingPact")
    void verifyRequestShipping() {
        assertNotNull("Pact verified");
    }
}
