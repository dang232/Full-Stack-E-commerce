package com.vnshop.orderservice.domain.port.out;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Resolves a coupon's discount against the authoritative source of truth
 * (coupon-service via HTTP) for the {@code /checkout/calculate} preview
 * path. The order-service-local {@code CouponValidator} stays in place
 * for the place-order-time flow (where coupon-service writes a usage row
 * via Kafka and order-service consumes it); this port is preview-only.
 *
 * <p>Returns {@link Optional#empty()} for any input that doesn't yield a
 * non-zero discount — invalid code, expired, exhausted, transport
 * failure. The buyer's actual {@code /checkout/apply-coupon} round-trip
 * (also routed at coupon-service) is what surfaces the human-readable
 * error message.</p>
 */
public interface CouponValidationPort {
    Optional<BigDecimal> resolveDiscount(String code, BigDecimal orderTotal, String userId);
}
