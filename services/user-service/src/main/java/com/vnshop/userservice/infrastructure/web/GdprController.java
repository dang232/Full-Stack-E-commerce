package com.vnshop.userservice.infrastructure.web;

import com.vnshop.userservice.application.GdprDeleteUseCase;
import com.vnshop.userservice.application.GdprExportUseCase;
import com.vnshop.userservice.domain.GdprExportRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/gdpr")
public class GdprController {
    private final GdprExportUseCase exportUseCase;
    private final GdprDeleteUseCase deleteUseCase;

    public GdprController(GdprExportUseCase exportUseCase, GdprDeleteUseCase deleteUseCase) {
        this.exportUseCase = exportUseCase;
        this.deleteUseCase = deleteUseCase;
    }

    @PostMapping("/export/{userId}")
    @PreAuthorize("#userId == authentication.name or hasRole('admin')")
    public ResponseEntity<Map<String, String>> requestExport(@PathVariable String userId) {
        String requestId = exportUseCase.initiateExport(userId);
        return ResponseEntity.accepted().body(Map.of("requestId", requestId, "status", "PENDING"));
    }

    @GetMapping("/export/{userId}/status/{requestId}")
    @PreAuthorize("#userId == authentication.name or hasRole('admin')")
    public ResponseEntity<Map<String, Object>> getExportStatus(
            @PathVariable String userId, @PathVariable String requestId) {
        GdprExportRequest request = exportUseCase.getExportStatus(userId, requestId);
        return ResponseEntity.ok(Map.of(
                "requestId", request.getRequestId(),
                "status", request.getStatus().name(),
                "missingServices", request.getMissingServices()));
    }

    @DeleteMapping("/delete/{userId}")
    @PreAuthorize("#userId == authentication.name or hasRole('admin')")
    public ResponseEntity<Map<String, String>> requestDeletion(@PathVariable String userId) {
        deleteUseCase.initiateDelete(userId);
        return ResponseEntity.accepted().body(Map.of("status", "DELETION_INITIATED"));
    }
}
