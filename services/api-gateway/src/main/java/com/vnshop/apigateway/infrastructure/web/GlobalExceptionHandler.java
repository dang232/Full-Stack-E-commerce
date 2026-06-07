package com.vnshop.apigateway.infrastructure.web;

import io.opentelemetry.api.trace.Span;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * Centralized exception handler for api-gateway that returns the standard VNShop error shape:
 * { code, message, details, timestamp, traceId }
 *
 * The gateway runs on Spring WebFlux (reactive), so this uses @RestControllerAdvice
 * for controller-level exceptions (e.g. FallbackController).
 * Gateway filter-level errors (rate limit 429, circuit breaker 503) are handled
 * by the gateway itself and return their own status codes.
 *
 * traceId is pulled from the active OTEL span.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> responseStatus(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) status = HttpStatus.INTERNAL_SERVER_ERROR;
        String code = status.name();
        return ResponseEntity.status(status)
            .body(ErrorResponse.of(code, ex.getReason() != null ? ex.getReason() : status.getReasonPhrase(), traceId()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> badRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
            .body(ErrorResponse.of("BAD_REQUEST", ex.getMessage(), traceId()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> internal(Exception ex) {
        log.error("Unhandled gateway exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse.of("INTERNAL_ERROR", "An unexpected error occurred", traceId()));
    }

    // ---------------------------------------------------------------------------

    private static String traceId() {
        String id = Span.current().getSpanContext().getTraceId();
        return "0000000000000000".equals(id) ? null : id;
    }
}
