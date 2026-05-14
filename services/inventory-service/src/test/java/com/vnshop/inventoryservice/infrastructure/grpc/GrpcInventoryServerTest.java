package com.vnshop.inventoryservice.infrastructure.grpc;

import com.vnshop.proto.inventory.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

class GrpcInventoryServerTest {
    private Server server;
    private ManagedChannel channel;
    private InventoryServiceGrpc.InventoryServiceBlockingStub stub;

    @BeforeEach
    void setUp() throws IOException {
        GrpcInventoryServer service = new GrpcInventoryServer();
        service.port = 0; // random port
        server = ServerBuilder.forPort(0).addService(service).build().start();
        channel = ManagedChannelBuilder.forAddress("localhost", server.getPort())
            .usePlaintext().build();
        stub = InventoryServiceGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void tearDown() {
        channel.shutdown();
        server.shutdown();
    }

    @Test
    void reserveReturnsSuccess() {
        ReserveResponse response = stub.reserve(ReserveRequest.newBuilder()
            .setOrderId("ord-1")
            .addItems(OrderItem.newBuilder().setProductId("p1").setQuantity(1).build())
            .build());
        assertTrue(response.getSuccess());
        assertEquals(1, response.getReservedItems());
    }

    @Test
    void reserveFailsWithEmptyItems() {
        assertThrows(Exception.class, () -> stub.reserve(ReserveRequest.newBuilder()
            .setOrderId("ord-1").build()));
    }

    @Test
    void releaseReturnsSuccess() {
        ReleaseResponse response = stub.release(ReleaseRequest.newBuilder()
            .setOrderId("ord-1").build());
        assertTrue(response.getSuccess());
    }
}
