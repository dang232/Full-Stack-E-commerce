package com.vnshop.orderservice.domain.port.out;

/**
 * Port for recording audit trail entries from application layer.
 * Infrastructure provides the concrete persistence mechanism.
 */
public interface AuditPort {
    void recordAction(String userId, String action, String resourceType, String resourceId);
}
