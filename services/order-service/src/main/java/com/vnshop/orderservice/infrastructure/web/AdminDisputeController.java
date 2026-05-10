package com.vnshop.orderservice.infrastructure.web;

import com.vnshop.orderservice.application.DisputeUseCase;
import com.vnshop.orderservice.domain.DisputeStatus;
import com.vnshop.orderservice.domain.port.out.DisputeRepositoryPort;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/disputes")
public class AdminDisputeController {
    private final DisputeUseCase disputeUseCase;
    private final DisputeRepositoryPort disputeRepositoryPort;

    public AdminDisputeController(DisputeUseCase disputeUseCase, DisputeRepositoryPort disputeRepositoryPort) {
        this.disputeUseCase = disputeUseCase;
        this.disputeRepositoryPort = disputeRepositoryPort;
    }

    @GetMapping("/open")
    public List<ReturnController.DisputeResponse> open() {
        return disputeRepositoryPort.findByStatus(DisputeStatus.OPEN.name()).stream()
                .map(ReturnController.DisputeResponse::fromDomain)
                .toList();
    }

    @PostMapping("/{disputeId}/resolve")
    public ReturnController.DisputeResponse resolve(@PathVariable String disputeId, @Valid @RequestBody ResolveDisputeRequest request) {
        return ReturnController.DisputeResponse.fromDomain(disputeUseCase.resolve(disputeId, request.adminResolution()));
    }

    public record ResolveDisputeRequest(@NotBlank String adminResolution) {
    }
}
