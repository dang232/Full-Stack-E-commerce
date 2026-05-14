package com.vnshop.inventoryservice.infrastructure.grpc;

import com.vnshop.proto.inventory.*;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.springframework.beans.factory.annotation.Value;
import java.io.IOException;

@Component
public class GrpcInventoryServer extends InventoryServiceGrpc.InventoryServiceImplBase {
    private static final Logger log = LoggerFactory.getLogger(GrpcInventoryServer.class);
    private Server server;
    
    @Value("${grpc.server.port:9093}")
    int port;

    @PostConstruct
    public void start() throws IOException {
        server = ServerBuilder.forPort(port)
            .addService(this)
            .build()
            .start();
        log.info("Inventory gRPC server started on port {}", port);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down Inventory gRPC server");
            GrpcInventoryServer.this.stop();
        }));
    }

    @PreDestroy
    public void stop() {
        if (server != null) {
            server.shutdown();
        }
    }

    @Override
    public void reserve(ReserveRequest request, StreamObserver<ReserveResponse> responseObserver) {
        if (request.getOrderId().isBlank()) {
            responseObserver.onError(new IllegalArgumentException("orderId must not be blank"));
            return;
        }
        if (request.getItemsCount() == 0) {
            responseObserver.onError(new IllegalArgumentException("items must not be empty"));
            return;
        }
        log.info("Reserving {} items for order {}", request.getItemsCount(), request.getOrderId());
        // TODO: delegate to domain use case (stub: always success)
        responseObserver.onNext(ReserveResponse.newBuilder()
            .setSuccess(true)
            .setReservedItems(request.getItemsCount())
            .build());
        responseObserver.onCompleted();
    }

    @Override
    public void release(ReleaseRequest request, StreamObserver<ReleaseResponse> responseObserver) {
        if (request.getOrderId().isBlank()) {
            responseObserver.onError(new IllegalArgumentException("orderId must not be blank"));
            return;
        }
        log.info("Releasing inventory for order {}", request.getOrderId());
        // TODO: delegate to domain use case (stub: always success)
        responseObserver.onNext(ReleaseResponse.newBuilder().setSuccess(true).build());
        responseObserver.onCompleted();
    }
}
