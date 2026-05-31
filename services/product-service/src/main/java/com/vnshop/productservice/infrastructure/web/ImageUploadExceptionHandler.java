package com.vnshop.productservice.infrastructure.web;

import com.vnshop.productservice.application.image.ProductImageValidationException;
import com.vnshop.productservice.application.review.image.ReviewImageValidationException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Global scope on purpose: both ProductImageUploadController and
// ReviewImageUploadController throw structured validation exceptions, and
// the ApiExceptionHandler's generic IllegalArgumentException -> 400 mapping
// would otherwise swallow the failure list. Pt20 audit added the
// ReviewImageValidationException registration; before that the review
// controller silently downgraded to a generic 400 with no details.
@RestControllerAdvice
public class ImageUploadExceptionHandler {
    @ExceptionHandler(ProductImageValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ErrorResponse productImageValidation(ProductImageValidationException exception) {
        return new ErrorResponse(new ErrorBody("product_image_validation_failed", exception.getMessage(), exception.failures()));
    }

    @ExceptionHandler(ReviewImageValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ErrorResponse reviewImageValidation(ReviewImageValidationException exception) {
        return new ErrorResponse(new ErrorBody("review_image_validation_failed", exception.getMessage(), exception.failures()));
    }

    public record ErrorResponse(ErrorBody error) {
    }

    public record ErrorBody(String code, String message, List<String> details) {
    }
}
