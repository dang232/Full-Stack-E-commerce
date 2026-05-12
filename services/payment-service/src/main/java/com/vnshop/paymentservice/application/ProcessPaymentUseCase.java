package com.vnshop.paymentservice.application;

import com.vnshop.paymentservice.domain.Payment;
import com.vnshop.paymentservice.domain.PaymentMethod;
import com.vnshop.paymentservice.domain.PaymentStatus;
import com.vnshop.paymentservice.domain.port.out.PaymentGatewayPort;
import com.vnshop.paymentservice.domain.port.out.PaymentRepositoryPort;
import com.vnshop.paymentservice.application.ledger.LedgerService;

import java.math.BigDecimal;
import java.util.Objects;

public class ProcessPaymentUseCase {
    private final PaymentRepositoryPort paymentRepositoryPort;
    private final PaymentGatewayPort paymentGatewayPort;
    private final LedgerService ledgerService;

    public ProcessPaymentUseCase(PaymentRepositoryPort paymentRepositoryPort, PaymentGatewayPort paymentGatewayPort, LedgerService ledgerService) {
        this.paymentRepositoryPort = Objects.requireNonNull(paymentRepositoryPort, "paymentRepositoryPort is required");
        this.paymentGatewayPort = Objects.requireNonNull(paymentGatewayPort, "paymentGatewayPort is required");
        this.ledgerService = Objects.requireNonNull(ledgerService, "ledgerService is required");
    }

    public Payment process(ProcessPaymentCommand command) {
        Payment pendingPayment = Payment.pending(command.orderId(), command.buyerId(), command.amount(), toDomain(command.method()));
        PaymentGatewayPort.GatewayPaymentResult result = paymentGatewayPort.processPayment(pendingPayment);
        Payment savedPayment = paymentRepositoryPort.save(pendingPayment.withResult(result.status(), result.transactionRef()));
        if (savedPayment.status() == PaymentStatus.COMPLETED) {
            ledgerService.recordPayment(savedPayment);
        }
        return savedPayment;
    }

    private PaymentMethod toDomain(PaymentMethodInput method) {
        return switch (method) {
            case COD -> PaymentMethod.COD;
            case VNPAY -> PaymentMethod.VNPAY;
            case MOMO -> PaymentMethod.MOMO;
        };
    }
}
