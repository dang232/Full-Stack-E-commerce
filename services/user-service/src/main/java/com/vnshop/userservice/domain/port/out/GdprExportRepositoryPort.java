package com.vnshop.userservice.domain.port.out;

import com.vnshop.userservice.domain.GdprExportRequest;
import java.util.Optional;

public interface GdprExportRepositoryPort {
    void save(GdprExportRequest request);
    Optional<GdprExportRequest> findByRequestId(String requestId);
    Optional<GdprExportRequest> findLatestByUserId(String userId);
    boolean hasRecentExport(String userId);
}
