package com.vnshop.orderservice.infrastructure.grpc;

import com.vnshop.proto.inventory.InventoryServiceGrpc;
import com.vnshop.proto.payment.PaymentServiceGrpc;
import com.vnshop.proto.shipping.ShippingServiceGrpc;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringJUnitConfig(classes = GrpcClientConfig.class)
@TestPropertySource(properties = {
    "grpc.client.inventory.host=localhost",
    "grpc.client.inventory.port=9093",
    "grpc.client.payment.host=localhost",
    "grpc.client.payment.port=9094",
    "grpc.client.shipping.host=localhost",
    "grpc.client.shipping.port=9095"
})
class GrpcClientConfigTest {

    @Autowired
    private InventoryServiceGrpc.InventoryServiceBlockingStub inventoryStub;

    @Autowired
    private PaymentServiceGrpc.PaymentServiceBlockingStub paymentStub;

    @Autowired
    private ShippingServiceGrpc.ShippingServiceBlockingStub shippingStub;

    @Test
    void inventoryStubIsAvailable() {
        assertNotNull(inventoryStub);
    }

    @Test
    void paymentStubIsAvailable() {
        assertNotNull(paymentStub);
    }

    @Test
    void shippingStubIsAvailable() {
        assertNotNull(shippingStub);
    }
}
