package com.vnshop.userservice.domain.port.out;

import com.vnshop.userservice.domain.GdprDeletionServiceStatus;
import java.util.List;

public interface GdprDeletionStatusPort {
    void initializeServiceStatuses(String requestId, String userId, List<String> serviceNames);
    void updateServiceStatus(String requestId, String serviceName,
                             GdprDeletionServiceStatus status, String errorMessage);
    List<String> findFailedServices(String requestId);
}
