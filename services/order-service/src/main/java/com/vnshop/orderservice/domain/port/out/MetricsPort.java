package com.vnshop.orderservice.domain.port.out;

/**
 * Port for recording business metrics from application layer.
 * Avoids coupling use cases to Micrometer/Prometheus infrastructure.
 */
public interface MetricsPort {
    Object startTimer();
    void stopTimer(Object timerSample);
    void recordOrderCreated();
    void recordOrderCancelled();
    void recordOrderCreationFailed();
}
