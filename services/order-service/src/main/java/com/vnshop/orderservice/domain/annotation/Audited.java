package com.vnshop.orderservice.domain.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a use-case method for audit logging.
 * The AuditAspect (in infrastructure) intercepts calls to methods with this
 * annotation and persists an audit record with user, action, and resource details.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {
    /** Human-readable action name, e.g. "CREATE_ORDER", "CANCEL_ORDER" */
    String action();

    /** Resource type being acted upon, e.g. "Order", "SubOrder" */
    String resourceType();
}
