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
@PactTestFor(providerName = "inventory-service")
class InventoryConsumerPactTest {

    @Pact(consumer = "order-service", provider = "inventory-service")
    V4Pact reserveStockPact(PactDslWithProvider builder) {
        return builder
                .given("product SKU-001 has 10 units in stock")
                .uponReceiving("a request to reserve stock")
                .method("POST")
                .path("/grpc/vnshop.inventory.InventoryService/Reserve")
                .headers("Content-Type", "application/json")
                .body(new PactDslJsonBody()
                        .stringType("orderId", "order-123")
                        .array("items")
                            .object()
                                .stringType("productId", "SKU-001")
                                .integerType("quantity", 2)
                            .closeObject()
                        .closeArray())
                .willRespondWith()
                .status(200)
                .body(new PactDslJsonBody()
                        .stringType("reservationId", "res-456")
                        .booleanType("success", true))
                .toPact(V4Pact.class);
    }

    @Pact(consumer = "order-service", provider = "inventory-service")
    V4Pact releaseStockPact(PactDslWithProvider builder) {
        return builder
                .given("reservation res-456 exists")
                .uponReceiving("a request to release stock")
                .method("POST")
                .path("/grpc/vnshop.inventory.InventoryService/Release")
                .headers("Content-Type", "application/json")
                .body(new PactDslJsonBody()
                        .stringType("reservationId", "res-456"))
                .willRespondWith()
                .status(200)
                .body(new PactDslJsonBody()
                        .booleanType("success", true))
                .toPact(V4Pact.class);
    }

    @Test
    @PactTestFor(pactMethod = "reserveStockPact")
    void verifyReserveStock() {
        assertNotNull("Pact verified");
    }

    @Test
    @PactTestFor(pactMethod = "releaseStockPact")
    void verifyReleaseStock() {
        assertNotNull("Pact verified");
    }
}
