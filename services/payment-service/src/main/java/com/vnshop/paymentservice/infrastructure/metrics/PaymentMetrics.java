package com.vnshop.paymentservice.infrastructure.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class PaymentMetrics {

    private final Counter paymentAttempts;
    private final Counter paymentFailures;
    private final Counter paymentSuccesses;
    private final Counter paymentRefunds;

    public PaymentMetrics(MeterRegistry registry) {
        this.paymentAttempts = Counter.builder("vnshop_payment_attempts_total")
                .description("Total payment attempts")
                .register(registry);

        this.paymentFailures = Counter.builder("vnshop_payment_failures_total")
                .description("Total payment failures")
                .register(registry);

        this.paymentSuccesses = Counter.builder("vnshop_payment_successes_total")
                .description("Total successful payments")
                .register(registry);

        this.paymentRefunds = Counter.builder("vnshop_payment_refunds_total")
                .description("Total payment refunds processed")
                .register(registry);
    }

    public void recordAttempt() { paymentAttempts.increment(); }
    public void recordFailure() { paymentFailures.increment(); }
    public void recordSuccess() { paymentSuccesses.increment(); }
    public void recordRefund() { paymentRefunds.increment(); }
}
