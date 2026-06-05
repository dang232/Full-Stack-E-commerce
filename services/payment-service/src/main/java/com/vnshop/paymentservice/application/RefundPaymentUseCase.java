package com.vnshop.paymentservice.application;

import com.vnshop.paymentservice.domain.Payment;
import com.vnshop.paymentservice.domain.PaymentStatus;
import com.vnshop.paymentservice.domain.port.out.PaymentRepositoryPort;
import com.vnshop.paymentservice.domain.port.out.RefundGatewayPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

/**
 * Application service: orchestrates the refund of a completed payment.
 *
 * <ol>
 *   <li>Locates the payment by {@code orderId}.</li>
 *   <li>Verifies the payment is in {@link PaymentStatus#COMPLETED} status.</li>
 *   <li>Selects the matching {@link RefundGatewayPort} adapter.</li>
 *   <li>Calls the gateway, marks the payment as {@link PaymentStatus#REFUNDED},
 *       and saves the updated record.</li>
 *   <li>Returns a {@link RefundResult} the caller can use to publish the
 *       {@code payment.refunded} event.</li>
 * </ol>
 *
 * <p>Event publishing is intentionally left to the infrastructure layer
 * (the Kafka listener that calls this use case) so the application layer
 * stays free of messaging infrastructure.
 */
public class RefundPaymentUseCase {

    private static final Logger log = LoggerFactory.getLogger(RefundPaymentUseCase.class);

    private final PaymentRepositoryPort paymentRepository;
    private final List<RefundGatewayPort> gateways;

    public RefundPaymentUseCase(PaymentRepositoryPort paymentRepository, List<RefundGatewayPort> gateways) {
        this.paymentRepository = Objects.requireNonNull(paymentRepository, "paymentRepository is required");
        this.gateways = Objects.requireNonNull(gateways, "gateways is required");
    }

    /**
     * @throws OrderNotFoundException          when no payment exists for the order
     * @throws PaymentNotRefundableException   when the payment is not in COMPLETED status
     * @throws UnsupportedPaymentMethodException when no gateway adapter supports the payment method
     */
    public RefundResult refund(RefundPaymentCommand command) {
        Objects.requireNonNull(command, "command is required");

        Payment payment = paymentRepository.findByOrderId(command.orderId())
                .orElseThrow(() -> new OrderNotFoundException(command.orderId()));

        if (payment.status() != PaymentStatus.COMPLETED) {
            throw new PaymentNotRefundableException(
                    "payment for orderId=" + command.orderId()
                            + " is not COMPLETED (status=" + payment.status() + ")");
        }

        String methodName = payment.method().name();
        RefundGatewayPort gateway = gateways.stream()
                .filter(g -> g.supports(methodName))
                .findFirst()
                .orElseThrow(() -> new UnsupportedPaymentMethodException(
                        "no refund gateway for payment method " + methodName));

        String transactionRef = payment.transactionRef();
        if (transactionRef == null || transactionRef.isBlank()) {
            throw new PaymentNotRefundableException(
                    "payment for orderId=" + command.orderId() + " has no gateway transaction reference stored");
        }

        String refundId = gateway.refund(
                payment.paymentId().toString(),
                transactionRef,
                payment.amount(),
                command.reason());

        log.info("refund-issued orderId={} method={} transactionRef={} refundId={}",
                command.orderId(), methodName, transactionRef, refundId);

        Payment refunded = payment.withResult(PaymentStatus.REFUNDED, transactionRef);
        paymentRepository.save(refunded);

        return new RefundResult(refunded, refundId);
    }

    /**
     * Carries the updated {@link Payment} and the gateway-assigned refund id
     * back to the infrastructure layer for event publishing.
     */
    public record RefundResult(Payment payment, String refundId) {
    }
}
