package com.vnshop.orderservice.application;

/**
 * Thrown when a caller attempts to act on an order they don't own. Buyer-side
 * (ViewOrderUseCase.viewForBuyer) and seller-side (ShipOrderUseCase,
 * AcceptOrderUseCase) both raise this, and {@code ApiExceptionHandler} maps
 * it to 403. Messages are intentionally generic — never echo the requested
 * id back, since that turns the response into an authorization-test oracle.
 */
public class OrderAccessDeniedException extends RuntimeException {
    public OrderAccessDeniedException(String message) {
        super(message);
    }
}
