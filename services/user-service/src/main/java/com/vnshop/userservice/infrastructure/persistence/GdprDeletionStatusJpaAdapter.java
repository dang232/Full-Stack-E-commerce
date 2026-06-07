package com.vnshop.userservice.infrastructure.persistence;

import com.vnshop.userservice.domain.GdprDeletionServiceStatus;
import com.vnshop.userservice.domain.port.out.GdprDeletionStatusPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class GdprDeletionStatusJpaAdapter implements GdprDeletionStatusPort {

    private final JdbcTemplate jdbcTemplate;

    public GdprDeletionStatusJpaAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void initializeServiceStatuses(String requestId, String userId, List<String> serviceNames) {
        for (String serviceName : serviceNames) {
            jdbcTemplate.update(
                    "INSERT INTO user_svc.gdpr_deletion_status (request_id, user_id, service_name, status) " +
                    "VALUES (?, ?, ?, 'PENDING') ON CONFLICT (request_id, service_name) DO NOTHING",
                    requestId, userId, serviceName);
        }
    }

    @Override
    public void updateServiceStatus(String requestId, String serviceName,
                                    GdprDeletionServiceStatus status, String errorMessage) {
        jdbcTemplate.update(
                "UPDATE user_svc.gdpr_deletion_status " +
                "SET status = ?, error_message = ?, updated_at = NOW() " +
                "WHERE request_id = ? AND service_name = ?",
                status.name(), errorMessage, requestId, serviceName);
    }

    @Override
    public List<String> findFailedServices(String requestId) {
        return jdbcTemplate.queryForList(
                "SELECT service_name FROM user_svc.gdpr_deletion_status " +
                "WHERE request_id = ? AND status = 'FAILED'",
                String.class, requestId);
    }
}
