package com.vnshop.orderservice.infrastructure.grpc;

import com.vnshop.orderservice.domain.OrderItem;
import com.vnshop.orderservice.domain.port.out.InventoryReservationPort;
import com.vnshop.proto.inventory.InventoryServiceGrpc;
import com.vnshop.proto.inventory.ReleaseRequest;
import com.vnshop.proto.inventory.ReleaseResponse;
import com.vnshop.proto.inventory.ReserveRequest;
import com.vnshop.proto.inventory.ReserveResponse;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
public class GrpcInventoryReservationAdapter implements InventoryReservationPort {

    private static final Logger LOGGER = LoggerFactory.getLogger(GrpcInventoryReservationAdapter.class);

    private final InventoryServiceGrpc.InventoryServiceBlockingStub inventoryStub;

    public GrpcInventoryReservationAdapter(InventoryServiceGrpc.InventoryServiceBlockingStub inventoryStub) {
        this.inventoryStub = Objects.requireNonNull(inventoryStub, "inventoryStub is required");
    }

    @Override
    public void reserve(String orderId, List<OrderItem> items) {
        Objects.requireNonNull(orderId, "orderId is required");
        Objects.requireNonNull(items, "items is required");
        if (items.isEmpty()) {
            throw new IllegalArgumentException("items must not be empty");
        }

        List<com.vnshop.proto.inventory.OrderItem> protoItems = items.stream()
                .map(item -> com.vnshop.proto.inventory.OrderItem.newBuilder()
                        .setProductId(item.productId())
                        .setVariant(item.variantSku())
                        .setQuantity(item.quantity())
                        .build())
                .toList();

        ReserveRequest request = ReserveRequest.newBuilder()
                .setOrderId(orderId)
                .addAllItems(protoItems)
                .build();

        LOGGER.info("Sending reserve request for order {} with {} items", orderId, protoItems.size());
        try {
            ReserveResponse response = inventoryStub.reserve(request);
            if (!response.getSuccess()) {
                throw new RuntimeException("Inventory reservation failed for order " + orderId);
            }
            LOGGER.info("Inventory reserved for order {}: {} items", orderId, response.getReservedItems());
        } catch (StatusRuntimeException e) {
            LOGGER.error("gRPC reserve call failed for order {}: {}", orderId, e.getMessage(), e);
            throw new RuntimeException("Inventory reservation failed for order " + orderId, e);
        }
    }

    @Override
    public void release(String orderId) {
        Objects.requireNonNull(orderId, "orderId is required");

        ReleaseRequest request = ReleaseRequest.newBuilder()
                .setOrderId(orderId)
                .build();

        LOGGER.info("Sending release request for order {}", orderId);
        try {
            ReleaseResponse response = inventoryStub.release(request);
            if (!response.getSuccess()) {
                throw new RuntimeException("Inventory release failed for order " + orderId);
            }
            LOGGER.info("Inventory released for order {}", orderId);
        } catch (StatusRuntimeException e) {
            LOGGER.error("gRPC release call failed for order {}: {}", orderId, e.getMessage(), e);
            throw new RuntimeException("Inventory release failed for order " + orderId, e);
        }
    }
}
