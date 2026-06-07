package com.vnshop.paymentservice.infrastructure.gateway;

import com.vnshop.paymentservice.domain.PaymentMethod;
import com.vnshop.paymentservice.domain.PaymentStatus;
import com.vnshop.paymentservice.domain.port.out.PaymentRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Scheduled job that expires VietQR payments whose bank credit never arrived.
 *
 * <p>Any PENDING VietQR payment older than {@code payment.vietqr.timeout-minutes}
 * (default: 10 min) transitions to {@link PaymentStatus#PAYMENT_TIMEOUT}. The
 * transition is idempotent — payments already in a terminal state are skipped by
 * the repository query.
 *
 * <p>The job runs every minute by default. Enabling SePay push-mode
 * ({@link com.vnshop.paymentservice.infrastructure.sepay.SepayWebhookController})
 * or poll-mode ({@link com.vnshop.paymentservice.infrastructure.sepay.SepayPoller})
 * will have promoted the payment to COMPLETED before the timeout fires for healthy
 * transactions, so this job only triggers on genuine missed payments.
 */
@Component
@ConditionalOnProperty(name = "payment.vietqr.enabled", havingValue = "true", matchIfMissing = true)
public class VietQrTimeoutJob {

    private static final Logger log = LoggerFactory.getLogger(VietQrTimeoutJob.class);

    private final PaymentRepositoryPort paymentRepository;
    private final long timeoutMinutes;

    public VietQrTimeoutJob(
            PaymentRepositoryPort paymentRepository,
            @Value("${payment.vietqr.timeout-minutes:10}") long timeoutMinutes) {
        this.paymentRepository = paymentRepository;
        this.timeoutMinutes = timeoutMinutes;
    }

    @Scheduled(fixedRateString = "${payment.vietqr.timeout-check-interval-seconds:60}000")
    @Transactional
    public void expireTimedOut() {
        Instant cutoff = Instant.now().minus(timeoutMinutes, ChronoUnit.MINUTES);
        List<com.vnshop.paymentservice.domain.Payment> stale =
                paymentRepository.findByMethodAndStatusAndCreatedAtBefore(
                        PaymentMethod.VIETQR, PaymentStatus.PENDING, cutoff);
        if (stale.isEmpty()) {
            return;
        }
        log.info("vietqr-timeout-sweep count={} cutoff={}", stale.size(), cutoff);
        for (com.vnshop.paymentservice.domain.Payment payment : stale) {
            paymentRepository.save(payment.withResult(PaymentStatus.PAYMENT_TIMEOUT,
                    "VIETQR-TIMEOUT-" + payment.paymentId()));
            log.info("vietqr-payment-timed-out paymentId={} orderId={} createdAt={}",
                    payment.paymentId(), payment.orderId(), payment.createdAt());
        }
    }
}
