package com.vnshop.orderservice.infrastructure.coupon;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vnshop.orderservice.domain.port.out.CouponValidationPort;
import java.math.BigDecimal;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP-backed adapter that resolves a coupon's discount via coupon-service.
 * Used by the {@code /checkout/calculate} preview path so the discount
 * shown to the buyer matches what the place-order flow will actually
 * deduct (coupon-service is the authoritative store for admin-published
 * coupons; order-service has its own legacy table that's only populated
 * via Kafka for usage tracking).
 *
 * <p>Failure semantics: any transport error, non-2xx response, or
 * "valid: false" body resolves to {@link Optional#empty()} — the preview
 * stays interactive with a zero discount and the buyer's actual
 * {@code /checkout/apply-coupon} attempt is what surfaces the
 * human-readable error.</p>
 */
public class CouponServiceAdapter implements CouponValidationPort {
    private static final Logger log = LoggerFactory.getLogger(CouponServiceAdapter.class);

    private final CouponServiceHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public CouponServiceAdapter(CouponServiceHttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<BigDecimal> resolveDiscount(String code, BigDecimal orderTotal, String userId) {
        if (code == null || code.isBlank() || orderTotal == null || orderTotal.signum() <= 0) {
            return Optional.empty();
        }
        try {
            String body = httpClient.validate(new CouponServiceHttpClient.ValidateCouponHttpRequest(
                    code, orderTotal, orderTotal, userId));
            if (body == null || body.isBlank()) return Optional.empty();
            JsonNode root = objectMapper.readTree(body);
            JsonNode data = root.has("data") ? root.path("data") : root;
            if (!data.path("valid").asBoolean(false)) return Optional.empty();
            JsonNode discount = data.path("discount");
            if (discount.isMissingNode() || discount.isNull()) return Optional.empty();
            BigDecimal amount = new BigDecimal(discount.asText("0"));
            if (amount.signum() <= 0) return Optional.empty();
            return Optional.of(amount);
        } catch (Exception ex) {
            // coupon-service down, parse failure, etc. Preview degrades to
            // zero discount; place-order-time apply still runs against
            // coupon-service and surfaces the real error to the user.
            log.warn("coupon-service validate failed (code={}): {}", code, ex.getMessage());
            return Optional.empty();
        }
    }
}
