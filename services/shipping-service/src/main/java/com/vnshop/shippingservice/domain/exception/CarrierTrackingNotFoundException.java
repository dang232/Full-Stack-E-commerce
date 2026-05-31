package com.vnshop.shippingservice.domain.exception;

/**
 * Thrown by a {@code CarrierGatewayPort} implementation when the carrier
 * confirms that the requested tracking code does not exist (HTTP 404 or the
 * provider's equivalent "not found" response). The application layer maps
 * this to {@link java.util.Optional#empty()} so the web layer can return a
 * structured 404. Other carrier failures (timeouts, 5xx, network errors)
 * MUST NOT use this exception so they can propagate as 500s.
 */
public class CarrierTrackingNotFoundException extends RuntimeException {
    public CarrierTrackingNotFoundException(String message) {
        super(message);
    }

    public CarrierTrackingNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
