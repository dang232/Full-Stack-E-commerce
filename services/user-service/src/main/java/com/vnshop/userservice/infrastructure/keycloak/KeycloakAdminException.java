package com.vnshop.userservice.infrastructure.keycloak;

/**
 * Marker exception for callers that need to map Keycloak Admin API outcomes
 * to HTTP status codes (typically 409 for duplicate users, 400 for password
 * policy violations).
 */
public class KeycloakAdminException extends RuntimeException {
    private final int statusCode;
    private final String errorCode;

    public KeycloakAdminException(int statusCode, String errorCode, String message) {
        super(message);
        this.statusCode = statusCode;
        this.errorCode = errorCode;
    }

    public int statusCode() {
        return statusCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
