package com.vnshop.orderservice.infrastructure.metrics;

import com.vnshop.orderservice.domain.port.out.MetricsPort;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class MetricsPortAdapter implements MetricsPort {
    private final OrderMetrics orderMetrics;

    public MetricsPortAdapter(OrderMetrics orderMetrics) {
        this.orderMetrics = orderMetrics;
    }

    @Override
    public Object startTimer() {
        return orderMetrics.startTimer();
    }

    @Override
    public void stopTimer(Object timerSample) {
        orderMetrics.stopTimer((Timer.Sample) timerSample);
    }

    @Override
    public void recordOrderCreated() {
        orderMetrics.recordOrderCreated();
    }

    @Override
    public void recordOrderCancelled() {
        orderMetrics.recordOrderCancelled();
    }

    @Override
    public void recordOrderCreationFailed() {
        orderMetrics.recordOrderCreationFailed();
    }
}
