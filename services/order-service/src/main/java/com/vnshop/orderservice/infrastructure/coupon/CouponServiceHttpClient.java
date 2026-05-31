package com.vnshop.orderservice.infrastructure.coupon;

import java.math.BigDecimal;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * Declarative HTTP client for coupon-service's preview endpoint. Spring
 * generates the proxy at startup via {@link CouponServiceHttpClientConfig};
 * callers never instantiate this directly.
 */
@HttpExchange
public interface CouponServiceHttpClient {

    /**
     * Calls coupon-service's {@code POST /checkout/validate-coupon} with the
     * canonical wire shape. Returns the envelope as raw JSON so the adapter
     * can parse it without coupling order-service to coupon-service classes.
     */
    @PostExchange("/checkout/validate-coupon")
    String validate(@RequestBody ValidateCouponHttpRequest request);

    record ValidateCouponHttpRequest(
            String code,
            BigDecimal orderAmount,
            BigDecimal orderTotal,
            String userId
    ) {
    }
}
