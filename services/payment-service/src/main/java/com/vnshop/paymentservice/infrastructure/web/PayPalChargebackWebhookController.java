package com.vnshop.paymentservice.infrastructure.web;

import com.vnshop.paymentservice.application.chargeback.ChargebackService;
import com.vnshop.paymentservice.domain.Chargeback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;

/**
 * Receives PayPal {@code CUSTOMER.DISPUTE.CREATED} webhook events and creates
 * a {@link Chargeback} record. PayPal authenticates via webhook ID verification
 * (header-based); for now we verify the {@code PAYPAL-AUTH-ALGO} header is
 * present as a basic guard — full cert-chain verification is a follow-up (Track 6.1).
 */
@RestController
@RequestMapping("/payment/paypal")
@ConditionalOnProperty(name = "payment.paypal.enabled", havingValue = "true")
public class PayPalChargebackWebhookController {

    private static final Logger log = LoggerFactory.getLogger(PayPalChargebackWebhookController.class);
    private static final String DISPUTE_CREATED = "CUSTOMER.DISPUTE.CREATED";

    private final ChargebackService chargebackService;

    public PayPalChargebackWebhookController(ChargebackService chargebackService) {
        this.chargebackService = Objects.requireNonNull(chargebackService, "chargebackService is required");
    }

    @PostMapping("/chargeback-webhook")
    public ResponseEntity<ApiResponse<ChargebackWebhookResponse>> chargebackWebhook(
            @RequestHeader(value = "PAYPAL-AUTH-ALGO", required = false) String authAlgo,
            @RequestBody Map<String, Object> payload) {

        if (authAlgo == null || authAlgo.isBlank()) {
            log.warn("paypal-chargeback-webhook-missing-auth-algo");
            return ResponseEntity.badRequest().body(ApiResponse.error("missing auth header", "BAD_SIGNATURE"));
        }

        String eventType = payload.getOrDefault("event_type", "").toString();
        if (!DISPUTE_CREATED.equals(eventType)) {
            return ResponseEntity.ok(ApiResponse.ok(new ChargebackWebhookResponse("ignored")));
        }

        Map<?, ?> resource = payload.get("resource") instanceof Map<?, ?> m ? m : Map.of();
        String disputeId = Objects.toString(resource.get("dispute_id"), "UNKNOWN");
        String orderId = extractOrderId(resource);
        String reason = Objects.toString(resource.get("reason"), "unspecified");
        LocalDate dueDate = null; // PayPal sends due date in dispute_life_cycle_stage details; left null here

        Chargeback result = chargebackService.createFromWebhook(
                orderId,
                disputeId,
                Chargeback.ChargebackProvider.PAYPAL,
                reason,
                dueDate);

        String outcome = result == null ? "duplicate" : result.id().toString();
        return ResponseEntity.ok(ApiResponse.ok(new ChargebackWebhookResponse(outcome)));
    }

    @SuppressWarnings("unchecked")
    private String extractOrderId(Map<?, ?> resource) {
        Object disputedTransactions = resource.get("disputed_transactions");
        if (disputedTransactions instanceof java.util.List<?> list && !list.isEmpty()
                && list.get(0) instanceof Map<?, ?> first) {
            Object invoiceNumber = first.get("invoice_number");
            if (invoiceNumber != null && !invoiceNumber.toString().isBlank()) {
                return invoiceNumber.toString();
            }
        }
        return "UNKNOWN";
    }

    public record ChargebackWebhookResponse(String outcome) {
    }
}
