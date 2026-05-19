package com.vnshop.paymentservice.infrastructure.gateway;

import com.vnshop.paymentservice.domain.Payment;
import com.vnshop.paymentservice.domain.PaymentMethod;
import com.vnshop.paymentservice.domain.PaymentStatus;
import com.vnshop.paymentservice.domain.port.out.PaymentGatewayPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * VietQR processing leaves the payment in PENDING and lets the buyer scan the
 * QR code rendered by {@code POST /payment/vietqr/create} (the controller does
 * the {@link VietQrService#generate} call after the use case persists this
 * PENDING row). Confirmation arrives later via the SePay poller (auto-confirm)
 * or the {@code AdminVietQrController} manual fallback — both go through
 * {@code PaymentPromotionService}.
 */
@Component
@ConditionalOnProperty(name = "payment.vietqr.enabled", havingValue = "true", matchIfMissing = true)
public class VietQrPaymentMethodHandler implements PaymentMethodHandler {
    private final VietQrService vietQrService;

    public VietQrPaymentMethodHandler(VietQrService vietQrService) {
        this.vietQrService = Objects.requireNonNull(vietQrService, "vietQrService is required");
    }

    @Override
    public PaymentMethod method() {
        return PaymentMethod.VIETQR;
    }

    @Override
    public PaymentGatewayPort.GatewayPaymentResult processPayment(Payment payment) {
        if (!vietQrService.isConfigured()) {
            return new PaymentGatewayPort.GatewayPaymentResult(PaymentStatus.FAILED, "VIETQR_NOT_CONFIGURED");
        }
        return new PaymentGatewayPort.GatewayPaymentResult(PaymentStatus.PENDING, "VIETQR-" + payment.paymentId());
    }
}
