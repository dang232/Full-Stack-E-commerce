package com.vnshop.orderservice.infrastructure.grpc;

import com.vnshop.proto.inventory.InventoryServiceGrpc;
import com.vnshop.proto.payment.PaymentServiceGrpc;
import com.vnshop.proto.shipping.ShippingServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GrpcClientConfig {

    @Value("${grpc.client.inventory.host:localhost}")
    private String inventoryHost;
    @Value("${grpc.client.inventory.port:9093}")
    private int inventoryPort;
    @Value("${grpc.client.payment.host:localhost}")
    private String paymentHost;
    @Value("${grpc.client.payment.port:9094}")
    private int paymentPort;
    @Value("${grpc.client.shipping.host:localhost}")
    private String shippingHost;
    @Value("${grpc.client.shipping.port:9095}")
    private int shippingPort;

    @Bean
    public InventoryServiceGrpc.InventoryServiceBlockingStub inventoryStub() {
        ManagedChannel channel = ManagedChannelBuilder
            .forAddress(inventoryHost, inventoryPort).usePlaintext().build();
        return InventoryServiceGrpc.newBlockingStub(channel);
    }

    @Bean
    public PaymentServiceGrpc.PaymentServiceBlockingStub paymentStub() {
        ManagedChannel channel = ManagedChannelBuilder
            .forAddress(paymentHost, paymentPort).usePlaintext().build();
        return PaymentServiceGrpc.newBlockingStub(channel);
    }

    @Bean
    public ShippingServiceGrpc.ShippingServiceBlockingStub shippingStub() {
        ManagedChannel channel = ManagedChannelBuilder
            .forAddress(shippingHost, shippingPort).usePlaintext().build();
        return ShippingServiceGrpc.newBlockingStub(channel);
    }
}
