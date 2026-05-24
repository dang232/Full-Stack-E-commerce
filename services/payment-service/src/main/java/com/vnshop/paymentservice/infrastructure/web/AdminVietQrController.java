package com.vnshop.paymentservice.infrastructure.web;

import com.vnshop.paymentservice.application.PaymentPromotionService;
import com.vnshop.paymentservice.domain.Payment;
import com.vnshop.paymentservice.domain.PaymentMethod;
import com.vnshop.paymentservice.domain.port.out.PaymentRepositoryPort;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Admin-only manual confirmation for VietQR payments. Sits on
 * {@code /admin/vietqr/**} (separate from {@link PaymentController}'s
 * {@code /payment/**} class-level prefix) so the gateway's
 * {@code /admin/vietqr/**} -> payment-service route resolves cleanly.
 *
 * <p>The merchant sees the bank credit notification (the buyer's transfer
 * reference encodes the payment id) and POSTs here to mark the payment
 * COMPLETED. Promotion goes through {@link PaymentPromotionService} so the
 * save + ledger emit + (optional) outbox emit happens atomically and stays
 * consistent with the Stripe webhook / PayPal capture / SePay poller paths.
 * Idempotent on already-completed payments. The gateway's {@code /admin/**}
 * matcher already enforces {@code ROLE_ADMIN}; no controller-side role check
 * needed.
 */
@RestController
@RequestMapping("/admin/vietqr")
public class AdminVietQrController {
    private final PaymentRepositoryPort paymentRepository;
    private final PaymentPromotionService promotionService;

    public AdminVietQrController(
            PaymentRepositoryPort paymentRepository,
            PaymentPromotionService promotionService) {
        this.paymentRepository = paymentRepository;
        this.promotionService = promotionService;
    }

    @PostMapping("/confirm/{paymentId}")
    public ApiResponse<PaymentResponse> confirm(
            @PathVariable String paymentId,
            @RequestBody(required = false) VietQrConfirmRequest request
    ) {
        UUID id = UUID.fromString(paymentId);
        // Pt39 audit: gateway already enforces ROLE_ADMIN so the leak risk
        // here is much lower than the buyer-facing endpoints, but the prior
        // code embedded the requested paymentId in three distinct error
        // messages — making the response a probe oracle even for a
        // compromised admin account. Constant messages keep the response
        // identical regardless of which branch tripped.
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("payment not found"));
        if (payment.method() != PaymentMethod.VIETQR) {
            throw new IllegalArgumentException("payment not found");
        }
        String txnRef = request != null && request.bankReference() != null && !request.bankReference().isBlank()
                ? request.bankReference()
                : "VIETQR-MANUAL-" + paymentId;
        PaymentPromotionService.PromotionResult result = promotionService.promote(
                PaymentPromotionService.PromotionCommand.manual(id, "VIETQR", txnRef));
        if (result.outcome() == PaymentPromotionService.PromotionResult.Outcome.PAYMENT_NOT_FOUND) {
            throw new IllegalArgumentException("payment not found");
        }
        return ApiResponse.ok(PaymentResponse.fromDomain(result.payment()));
    }
}

