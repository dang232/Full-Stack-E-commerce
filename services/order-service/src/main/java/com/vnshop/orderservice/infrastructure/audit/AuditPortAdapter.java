package com.vnshop.orderservice.infrastructure.audit;

import com.vnshop.orderservice.domain.port.out.AuditPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class AuditPortAdapter implements AuditPort {

    private static final Logger log = LoggerFactory.getLogger(AuditPortAdapter.class);

    private final AuditLogRepository auditLogRepository;

    public AuditPortAdapter(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = Objects.requireNonNull(auditLogRepository);
    }

    @Override
    public void recordAction(String userId, String action, String resourceType, String resourceId) {
        try {
            var auditEntry = new AuditLogJpaEntity(
                    userId,
                    null,       // userRole — not available via port
                    action,
                    resourceType,
                    resourceId,
                    null,       // details
                    null,       // ipAddress
                    null        // correlationId
            );
            auditLogRepository.save(auditEntry);
        } catch (Exception e) {
            log.error("Failed to persist audit log for action={}, resourceType={}, resourceId={}",
                    action, resourceType, resourceId, e);
        }
    }
}
