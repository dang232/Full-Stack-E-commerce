package com.vnshop.shippingservice.infrastructure.grpc;

import com.vnshop.proto.shipping.ShippingRequest;
import com.vnshop.proto.shipping.ShippingResponse;
import com.vnshop.proto.shipping.ShippingServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class GrpcShippingServerTest {
    private Server server;
    private ManagedChannel channel;
    private ShippingServiceGrpc.ShippingServiceBlockingStub stub;

    @BeforeEach
    void setUp() throws IOException {
        GrpcShippingServer service = new GrpcShippingServer();
        service.port = 0; // random port
        server = ServerBuilder.forPort(0).addService(service).build().start();
        channel = ManagedChannelBuilder.forAddress("localhost", server.getPort())
                .usePlaintext().build();
        stub = ShippingServiceGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void tearDown() {
        channel.shutdown();
        server.shutdown();
    }

    @Test
    void requestShippingReturnsLabels() {
        ShippingResponse response = stub.requestShipping(ShippingRequest.newBuilder()
                .setOrderId("ord-1001")
                .addSubOrders(com.vnshop.proto.shipping.SubOrder.newBuilder()
                        .setSellerId("seller-1")
                        .addItems(com.vnshop.proto.shipping.SubOrderItem.newBuilder()
                                .setProductId("prod-1")
                                .setVariant("red")
                                .setQuantity(2)
                                .build())
                        .setShippingAddress(com.vnshop.proto.shipping.ShippingAddress.newBuilder()
                                .setFullName("Nguyen Van A")
                                .setPhone("0901234567")
                                .setStreet("123 Le Loi")
                                .setCity("Ho Chi Minh")
                                .setProvince("Ho Chi Minh")
                                .build())
                        .build())
                .build());

        assertTrue(response.getSuccess());
        assertFalse(response.getLabelsList().isEmpty());
        assertEquals(1, response.getLabelsCount());

        var label = response.getLabels(0);
        assertNotNull(label.getTrackingCode());
        assertFalse(label.getTrackingCode().isBlank());
        assertNotNull(label.getCarrier());
        assertFalse(label.getCarrier().isBlank());
        assertNotNull(label.getEstimatedDelivery());
        assertFalse(label.getEstimatedDelivery().isBlank());
    }

    @Test
    void requestShippingFailsWithEmptySubOrders() {
        assertThrows(Exception.class, () -> stub.requestShipping(
                ShippingRequest.newBuilder()
                        .setOrderId("ord-1002")
                        .build()));
    }

    @Test
    void requestShippingFailsWithBlankOrderId() {
        assertThrows(Exception.class, () -> stub.requestShipping(
                ShippingRequest.newBuilder()
                        .setOrderId("")
                        .addSubOrders(com.vnshop.proto.shipping.SubOrder.newBuilder()
                                .setSellerId("seller-1")
                                .addItems(com.vnshop.proto.shipping.SubOrderItem.newBuilder()
                                        .setProductId("p1")
                                        .setQuantity(1)
                                        .build())
                                .setShippingAddress(com.vnshop.proto.shipping.ShippingAddress.newBuilder()
                                        .setFullName("Test")
                                        .setPhone("000")
                                        .setStreet("Street")
                                        .setCity("City")
                                        .setProvince("Province")
                                        .build())
                                .build())
                        .build()));
    }

    @Test
    void labelsAreNonEmptyForValidRequest() {
        ShippingResponse response = stub.requestShipping(ShippingRequest.newBuilder()
                .setOrderId("ord-1003")
                .addSubOrders(com.vnshop.proto.shipping.SubOrder.newBuilder()
                        .setSellerId("seller-1")
                        .addItems(com.vnshop.proto.shipping.SubOrderItem.newBuilder()
                                .setProductId("prod-1")
                                .setQuantity(1)
                                .build())
                        .setShippingAddress(com.vnshop.proto.shipping.ShippingAddress.newBuilder()
                                .setFullName("Tran Thi B")
                                .setPhone("0912345678")
                                .setStreet("456 Nguyen Hue")
                                .setCity("Ha Noi")
                                .setProvince("Ha Noi")
                                .build())
                        .build())
                .addSubOrders(com.vnshop.proto.shipping.SubOrder.newBuilder()
                        .setSellerId("seller-2")
                        .addItems(com.vnshop.proto.shipping.SubOrderItem.newBuilder()
                                .setProductId("prod-2")
                                .setVariant("blue")
                                .setQuantity(3)
                                .build())
                        .setShippingAddress(com.vnshop.proto.shipping.ShippingAddress.newBuilder()
                                .setFullName("Le Van C")
                                .setPhone("0923456789")
                                .setStreet("789 Tran Hung Dao")
                                .setCity("Da Nang")
                                .setProvince("Da Nang")
                                .build())
                        .build())
                .build());

        assertTrue(response.getSuccess());
        assertEquals(2, response.getLabelsCount());
        response.getLabelsList().forEach(label -> {
            assertNotNull(label.getTrackingCode());
            assertFalse(label.getTrackingCode().isBlank());
            assertNotNull(label.getCarrier());
            assertFalse(label.getCarrier().isBlank());
            assertNotNull(label.getEstimatedDelivery());
            assertFalse(label.getEstimatedDelivery().isBlank());
        });
    }
}
