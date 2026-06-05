package com.vnshop.paymentservice.application;

/**
 * Input command for {@link RefundPaymentUseCase}.
 *
 * @param orderId   order to be refunded — used to locate the Payment record
 * @param sagaId    saga correlation id; forwarded to {@code payment.refunded}
 *                  so the order-service SagaCompensationListener can close
 *                  the compensation step
 * @param reason    free-text reason forwarded to the gateway for dispute support
 */
public record RefundPaymentCommand(
        String orderId,
        String sagaId,
        String reason) {
}
