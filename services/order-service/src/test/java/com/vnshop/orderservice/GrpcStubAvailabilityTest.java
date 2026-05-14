package com.vnshop.orderservice;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GrpcStubAvailabilityTest {
    @Test
    void inventoryServiceStubExists() throws ClassNotFoundException {
        Class.forName("com.vnshop.proto.inventory.InventoryServiceGrpc");
        Class.forName("com.vnshop.proto.inventory.ReserveRequest");
    }

    @Test
    void paymentServiceStubExists() throws ClassNotFoundException {
        Class.forName("com.vnshop.proto.payment.PaymentServiceGrpc");
        Class.forName("com.vnshop.proto.payment.PaymentRequest");
    }

    @Test
    void shippingServiceStubExists() throws ClassNotFoundException {
        Class.forName("com.vnshop.proto.shipping.ShippingServiceGrpc");
        Class.forName("com.vnshop.proto.shipping.ShippingRequest");
    }

    @Test
    void commonMoneyExists() throws ClassNotFoundException {
        Class.forName("com.vnshop.proto.common.Money");
    }
}
