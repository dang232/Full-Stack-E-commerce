package com.vnshop.orderservice.infrastructure.web;

import java.time.Instant;
import java.util.List;

/**
 * Standard error response shape returned by GlobalExceptionHandler.
 * Matches the VNShop API error contract:
 * { code, message, details, timestamp, traceId }
 */
public record ErrorResponse(
    String code,
    String message,
    List<String> details,
    String timestamp,
    String traceId
) {
    public static ErrorResponse of(String code, String message, List<String> details, String traceId) {
        return new ErrorResponse(code, message, details, Instant.now().toString(), traceId);
    }

    public static ErrorResponse of(String code, String message, String traceId) {
        return of(code, message, List.of(), traceId);
    }
}
