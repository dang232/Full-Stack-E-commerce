package com.vnshop.userservice.infrastructure.web;

import java.lang.reflect.Method;

final class SecurityKeycloakId {
    private static final String STUB_KEYCLOAK_ID = "stub-keycloak-id";

    private SecurityKeycloakId() {
    }

    static String current() {
        try {
            Class<?> holderClass = Class.forName("org.springframework.security.core.context.SecurityContextHolder");
            Object context = holderClass.getMethod("getContext").invoke(null);
            Object authentication = context.getClass().getMethod("getAuthentication").invoke(context);
            if (authentication == null) {
                return STUB_KEYCLOAK_ID;
            }
            Object principal = authentication.getClass().getMethod("getPrincipal").invoke(authentication);
            String subject = jwtSubject(principal);
            if (subject != null) {
                return subject;
            }
            Object name = authentication.getClass().getMethod("getName").invoke(authentication);
            return name == null || name.toString().isBlank() ? STUB_KEYCLOAK_ID : name.toString();
        } catch (ReflectiveOperationException ex) {
            return STUB_KEYCLOAK_ID;
        }
    }

    private static String jwtSubject(Object principal) throws ReflectiveOperationException {
        if (principal == null) {
            return null;
        }
        Method getSubject;
        try {
            getSubject = principal.getClass().getMethod("getSubject");
        } catch (NoSuchMethodException ex) {
            return null;
        }
        Object subject = getSubject.invoke(principal);
        return subject == null || subject.toString().isBlank() ? null : subject.toString();
    }
}
