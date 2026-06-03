package com.vnshop.orderservice.infrastructure.grpc;

import com.vnshop.orderservice.domain.Address;
import com.vnshop.orderservice.domain.OrderItem;
import com.vnshop.orderservice.domain.SubOrder;
import com.vnshop.orderservice.domain.port.out.ShippingRequestPort;
import com.vnshop.proto.shipping.ShippingServiceGrpc;
import com.vnshop.proto.shipping.ShippingRequest;
import com.vnshop.proto.shipping.ShippingResponse;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnBean(ShippingServiceGrpc.ShippingServiceBlockingStub.class)
public class GrpcShippingRequestAdapter implements ShippingRequestPort {

    private static final Logger LOGGER = LoggerFactory.getLogger(GrpcShippingRequestAdapter.class);

    private final ShippingServiceGrpc.ShippingServiceBlockingStub shippingStub;

    public GrpcShippingRequestAdapter(ShippingServiceGrpc.ShippingServiceBlockingStub shippingStub) {
        this.shippingStub = shippingStub;
    }

    @Override
    public void requestShipping(String orderId, SubOrder subOrder, Address shippingAddress) {
        ShippingRequest request = ShippingRequest.newBuilder()
                .setOrderId(orderId)
                .addSubOrders(com.vnshop.proto.shipping.SubOrder.newBuilder()
                        .setSellerId(subOrder.sellerId())
                        .addAllItems(subOrder.items().stream()
                                .map(GrpcShippingRequestAdapter::toProtoItem)
                                .toList())
                        .setShippingAddress(toProtoAddress(shippingAddress))
                        .build())
                .build();

        try {
            ShippingResponse response = shippingStub
                    .withDeadlineAfter(5, TimeUnit.SECONDS)
                    .requestShipping(request);

            if (!response.getSuccess()) {
                LOGGER.warn("Shipping request failed for order {} seller {}", orderId, subOrder.sellerId());
            } else {
                LOGGER.info("Shipping request submitted for order {} seller {} — {} label(s)",
                        orderId, subOrder.sellerId(), response.getLabelsCount());
            }
        } catch (StatusRuntimeException e) {
            LOGGER.error("gRPC shipping request failed: orderId={}, code={}, message={}",
                    orderId, e.getStatus().getCode(), e.getStatus().getDescription(), e);
            throw new RuntimeException("Shipping request failed for order " + orderId, e);
        }
    }

    private static com.vnshop.proto.shipping.SubOrderItem toProtoItem(OrderItem item) {
        return com.vnshop.proto.shipping.SubOrderItem.newBuilder()
                .setProductId(item.productId())
                .setVariant(item.variantSku())
                .setQuantity(item.quantity())
                .build();
    }

    private static com.vnshop.proto.shipping.ShippingAddress toProtoAddress(Address address) {
        return com.vnshop.proto.shipping.ShippingAddress.newBuilder()
                .setFullName("")
                .setPhone("")
                .setStreet(address.street())
                .setCity(address.city())
                .setProvince(address.district())
                .build();
    }
}
