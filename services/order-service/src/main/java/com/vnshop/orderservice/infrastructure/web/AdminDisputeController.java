package com.vnshop.orderservice.infrastructure.web;

import com.vnshop.orderservice.application.DisputeUseCase;
import com.vnshop.orderservice.application.ListOpenDisputesUseCase;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin/disputes")
public class AdminDisputeController {
    private final DisputeUseCase disputeUseCase;
    private final ListOpenDisputesUseCase listOpenDisputesUseCase;

    public AdminDisputeController(DisputeUseCase disputeUseCase, ListOpenDisputesUseCase listOpenDisputesUseCase) {
        this.disputeUseCase = disputeUseCase;
        this.listOpenDisputesUseCase = listOpenDisputesUseCase;
    }

    @GetMapping("/open")
    public ApiResponse<List<DisputeResponse>> open() {
        return ApiResponse.ok(listOpenDisputesUseCase.listOpen().stream()
                .map(DisputeResponse::fromDomain)
                .toList());
    }

    @PostMapping("/{disputeId}/resolve")
    public ApiResponse<DisputeResponse> resolve(@PathVariable UUID disputeId, @Valid @RequestBody ResolveDisputeRequest request) {
        return ApiResponse.ok(DisputeResponse.fromDomain(disputeUseCase.resolve(disputeId, request.adminResolution())));
    }
}
