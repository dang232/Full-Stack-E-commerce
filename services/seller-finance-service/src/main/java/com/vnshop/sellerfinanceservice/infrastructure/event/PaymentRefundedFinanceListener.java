package com.vnshop.sellerfinanceservice.infrastructure.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vnshop.sellerfinanceservice.application.RefundWalletUseCase;
import com.vnshop.sellerfinanceservice.domain.CommissionTier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Reverses the seller wallet credit when a refund is issued. Payment-service
 * publishes {@code payment.refunded} as a flat JSON object (no outbox envelope —
 * payment-service uses a direct {@code KafkaTemplate.send}, unlike order-service
 * which goes through the outbox). The {@code amount} field is the buyer-facing
 * VND total; this listener applies the same commission tier as the original
 * credit so the wallet projection ends up where it started.
 *
 * <p>Idempotency is intentionally NOT handled here: a redelivery would
 * double-debit the wallet. Today the publish path is the at-least-once Kafka
 * primitive, but in practice {@code PayPalGateway.refund} is itself idempotent
 * via the PayPal-Request-Id header — meaning a true duplicate {@code payment.refunded}
 * shouldn't fire because the upstream listener will short-circuit on the
 * existing refund record. If that assumption breaks (e.g. the upstream listener
 * fails after PayPal succeeds and Kafka redelivers), a refund-ledger row will
 * be the right fix; deferring until that gap is real.
 */
@Service
public class PaymentRefundedFinanceListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentRefundedFinanceListener.class);

    private final RefundWalletUseCase refundWalletUseCase;
    private final ObjectMapper objectMapper;

    public PaymentRefundedFinanceListener(RefundWalletUseCase refundWalletUseCase, ObjectMapper objectMapper) {
        this.refundWalletUseCase = refundWalletUseCase;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "payment.refunded", groupId = "seller-finance-service-refund")
    public void onPaymentRefunded(String eventJson) {
        JsonNode payload = readTree(eventJson);
        String sellerId = text(payload, "sellerId");
        String amountRaw = text(payload, "amount");
        if (sellerId == null || sellerId.isBlank() || amountRaw == null || amountRaw.isBlank()) {
            LOGGER.warn("payment.refunded missing sellerId or amount — skipping. payload={}", eventJson);
            return;
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(amountRaw);
        } catch (NumberFormatException ex) {
            LOGGER.warn("payment.refunded amount={} not a valid number — skipping", amountRaw);
            return;
        }
        // Read commission tier from the event; default to STANDARD for backward
        // compatibility with events published before the tier field was added.
        String tierRaw = text(payload, "commissionTier");
        CommissionTier tier = CommissionTier.STANDARD;
        if (tierRaw != null && !tierRaw.isBlank()) {
            try {
                tier = CommissionTier.valueOf(tierRaw);
            } catch (IllegalArgumentException ex) {
                LOGGER.warn("payment.refunded unknown commissionTier={} — defaulting to STANDARD", tierRaw);
            }
        }
        refundWalletUseCase.refund(sellerId, amount, tier).ifPresentOrElse(
                result -> LOGGER.info("seller-wallet-refunded sellerId={} debited={}", sellerId, result.sellerNet()),
                () -> LOGGER.warn("seller-wallet-refund skipped — no wallet for sellerId={}", sellerId));
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            throw new IllegalArgumentException("payment.refunded payload is not valid JSON", ex);
        }
    }

    private static String text(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }
}
