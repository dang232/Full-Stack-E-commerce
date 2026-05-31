package com.vnshop.inventoryservice.application;

/**
 * Pt22 audit fix: thrown when a JWT-authenticated caller attempts to act on a
 * flash-sale reservation they do not own. {@link com.vnshop.inventoryservice.infrastructure.web.ApiExceptionHandler}
 * (or whichever advice picks it up) maps this to HTTP 403.
 */
public class FlashSaleAccessDeniedException extends RuntimeException {
    public FlashSaleAccessDeniedException(String message) {
        super(message);
    }
}
