package com.vnshop.orderservice.infrastructure.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class OrderMetrics {

    private final Counter ordersCreated;
    private final Counter ordersCancelled;
    private final Counter ordersFailedCreation;
    private final Timer orderCreationDuration;

    public OrderMetrics(MeterRegistry registry) {
        this.ordersCreated = Counter.builder("vnshop_orders_created_total")
                .description("Total orders successfully created")
                .register(registry);

        this.ordersCancelled = Counter.builder("vnshop_orders_cancelled_total")
                .description("Total orders cancelled")
                .register(registry);

        this.ordersFailedCreation = Counter.builder("vnshop_orders_creation_failed_total")
                .description("Total order creation failures")
                .register(registry);

        this.orderCreationDuration = Timer.builder("vnshop_order_creation_duration_seconds")
                .description("Order creation latency")
                .register(registry);
    }

    public void recordOrderCreated() { ordersCreated.increment(); }
    public void recordOrderCancelled() { ordersCancelled.increment(); }
    public void recordOrderCreationFailed() { ordersFailedCreation.increment(); }
    public Timer.Sample startTimer() { return Timer.start(); }
    public void stopTimer(Timer.Sample sample) { sample.stop(orderCreationDuration); }
}
