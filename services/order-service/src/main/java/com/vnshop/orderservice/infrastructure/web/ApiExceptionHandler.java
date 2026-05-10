package com.vnshop.orderservice.infrastructure.web;

import com.vnshop.orderservice.domain.InvoiceAccessDeniedException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(InvoiceAccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Map<String, ErrorResponse> forbidden(InvoiceAccessDeniedException exception) {
        return error("invoice_access_denied", exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, ErrorResponse> badRequest(IllegalArgumentException exception) {
        return error("bad_request", exception.getMessage());
    }

    private Map<String, ErrorResponse> error(String code, String message) {
        return Map.of("error", new ErrorResponse(code, message));
    }

    public record ErrorResponse(String code, String message) {
    }
}
