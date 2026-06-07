package com.vnshop.paymentservice.infrastructure.web;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Dispute;
import com.stripe.model.Event;
import com.stripe.model.StripeObject;
import com.vnshop.paymentservice.application.chargeback.ChargebackService;
import com.vnshop.paymentservice.domain.Chargeback;
import com.vnshop.paymentservice.infrastructure.stripe.StripeProperties;
import com.vnshop.paymentservice.infrastructure.stripe.StripeWebhookVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Objects;

/**
 * Receives Stripe {@code charge.dispute.created} webhook events and creates
 * a {@link Chargeback} record. Uses the same signature verification path as
 * {@link StripeWebhookController}. Idempotency is handled inside
 * {@link ChargebackService#createFromWebhook} via the {@code externalChargebackId}.
 */
@RestController
@RequestMapping("/payment/stripe")
@ConditionalOnProperty(name = "payment.stripe.enabled", havingValue = "true")
public class StripeChargebackWebhookController {

    private static final Logger log = LoggerFactory.getLogger(StripeChargebackWebhookController.class);
    private static final String DISPUTE_CREATED = "charge.dispute.created";

    private final StripeProperties properties;
    private final StripeWebhookVerifier verifier;
    private final ChargebackService chargebackService;

    public StripeChargebackWebhookController(StripeProperties properties,
                                              StripeWebhookVerifier verifier,
                                              ChargebackService chargebackService) {
        this.properties = Objects.requireNonNull(properties, "properties is required");
        this.verifier = Objects.requireNonNull(verifier, "verifier is required");
        this.chargebackService = Objects.requireNonNull(chargebackService, "chargebackService is required");
    }

    @PostMapping("/chargeback-webhook")
    public ResponseEntity<ApiResponse<ChargebackWebhookResponse>> chargebackWebhook(
            @RequestHeader(value = "Stripe-Signature", required = false) String signatureHeader,
            @RequestBody String payload) {

        Event event;
        try {
            event = verifier.constructEvent(payload, signatureHeader, properties.webhookSecret());
        } catch (SignatureVerificationException ex) {
            log.warn("stripe-chargeback-webhook-bad-signature: {}", ex.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error("invalid signature", "BAD_SIGNATURE"));
        } catch (RuntimeException ex) {
            log.warn("stripe-chargeback-webhook-malformed: {}", ex.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error("malformed payload", "BAD_REQUEST"));
        }

        if (!DISPUTE_CREATED.equals(event.getType())) {
            return ResponseEntity.ok(ApiResponse.ok(new ChargebackWebhookResponse(event.getId(), "ignored")));
        }

        StripeObject obj = event.getDataObjectDeserializer().getObject().orElse(null);
        if (!(obj instanceof Dispute dispute)) {
            log.warn("stripe-chargeback-deserialise-failed event={}", event.getId());
            return ResponseEntity.badRequest().body(ApiResponse.error("payload mismatch", "BAD_PAYLOAD"));
        }

        String orderId = dispute.getMetadata() != null
                ? dispute.getMetadata().getOrDefault("orderId", "UNKNOWN")
                : "UNKNOWN";
        LocalDate dueDate = dispute.getEvidenceDetails() != null
                && dispute.getEvidenceDetails().getDueBy() != null
                ? Instant.ofEpochSecond(dispute.getEvidenceDetails().getDueBy())
                .atZone(ZoneOffset.UTC).toLocalDate()
                : null;

        Chargeback result = chargebackService.createFromWebhook(
                orderId,
                dispute.getId(),
                Chargeback.ChargebackProvider.STRIPE,
                dispute.getReason() != null ? dispute.getReason() : "unspecified",
                dueDate);

        String outcome = result == null ? "duplicate" : result.id().toString();
        return ResponseEntity.ok(ApiResponse.ok(new ChargebackWebhookResponse(event.getId(), outcome)));
    }

    public record ChargebackWebhookResponse(String eventId, String outcome) {
    }
}
