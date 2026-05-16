package com.vnshop.orderservice.infrastructure.web;

import com.vnshop.orderservice.domain.InvoiceAccessDeniedException;
import com.vnshop.orderservice.domain.coupon.CouponException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(InvoiceAccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiResponse<Void> forbidden(InvoiceAccessDeniedException exception) {
        return ApiResponse.error(exception.getMessage(), "INVOICE_ACCESS_DENIED");
    }

    @ExceptionHandler(CouponException.class)
    public ResponseEntity<ApiResponse<Void>> coupon(CouponException exception) {
        HttpStatus status = switch (exception.code()) {
            case "COUPON_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "COUPON_CODE_DUPLICATE" -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(ApiResponse.error(exception.getMessage(), exception.code()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> badRequest(IllegalArgumentException exception) {
        return ApiResponse.error(exception.getMessage(), "BAD_REQUEST");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> internal(Exception exception) {
        // Log the full stack so operators can pinpoint the failing query/code path —
        // the generic INTERNAL_ERROR response leaves callers blind otherwise.
        log.error("Unhandled exception bubbled to ApiExceptionHandler", exception);
        return ApiResponse.error("An unexpected error occurred", "INTERNAL_ERROR");
    }
}
