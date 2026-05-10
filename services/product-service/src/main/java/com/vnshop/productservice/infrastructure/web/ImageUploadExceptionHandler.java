package com.vnshop.productservice.infrastructure.web;

import com.vnshop.productservice.application.image.ProductImageValidationException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = ProductImageUploadController.class)
public class ImageUploadExceptionHandler {
    @ExceptionHandler(ProductImageValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ErrorResponse imageValidation(ProductImageValidationException exception) {
        return new ErrorResponse(new ErrorBody("product_image_validation_failed", exception.getMessage(), exception.failures()));
    }

    public record ErrorResponse(ErrorBody error) {
    }

    public record ErrorBody(String code, String message, List<String> details) {
    }
}
