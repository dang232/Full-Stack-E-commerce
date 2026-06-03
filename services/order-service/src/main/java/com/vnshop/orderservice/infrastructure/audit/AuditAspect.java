package com.vnshop.orderservice.infrastructure.audit;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Objects;

@Aspect
@Component
public class AuditAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);

    private final AuditLogRepository auditLogRepository;

    public AuditAspect(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = Objects.requireNonNull(auditLogRepository);
    }

    @Around("@annotation(audited)")
    public Object audit(ProceedingJoinPoint joinPoint, Audited audited) throws Throwable {
        Object result = joinPoint.proceed();

        try {
            String userId = extractUserId();
            String userRole = extractUserRole();
            String resourceId = extractResourceId(result);
            String ipAddress = extractIpAddress();
            String correlationId = extractCorrelationId();

            var auditEntry = new AuditLogJpaEntity(
                    userId,
                    userRole,
                    audited.action(),
                    audited.resourceType(),
                    resourceId,
                    null,
                    ipAddress,
                    correlationId
            );

            auditLogRepository.save(auditEntry);
        } catch (Exception e) {
            // Audit logging must never break the business flow
            log.error("Failed to persist audit log for action={}, resource={}",
                    audited.action(), audited.resourceType(), e);
        }

        return result;
    }

    private String extractUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            return jwt.getSubject();
        }
        return "anonymous";
    }

    private String extractUserRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getAuthorities() != null) {
            return auth.getAuthorities().stream()
                    .map(Object::toString)
                    .filter(r -> r.startsWith("ROLE_"))
                    .findFirst()
                    .orElse("UNKNOWN");
        }
        return "UNKNOWN";
    }

    private String extractResourceId(Object result) {
        if (result == null) return null;
        try {
            var method = result.getClass().getMethod("getId");
            Object id = method.invoke(result);
            return id != null ? id.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String extractIpAddress() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servletAttrs) {
            var request = servletAttrs.getRequest();
            String forwarded = request.getHeader("X-Forwarded-For");
            return forwarded != null ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
        }
        return null;
    }

    private String extractCorrelationId() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servletAttrs) {
            return servletAttrs.getRequest().getHeader("X-Correlation-Id");
        }
        return null;
    }
}
