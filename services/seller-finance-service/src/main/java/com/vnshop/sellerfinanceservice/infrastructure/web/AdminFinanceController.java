package com.vnshop.sellerfinanceservice.infrastructure.web;

import com.vnshop.sellerfinanceservice.application.ProcessPayoutUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/finance")
public class AdminFinanceController {
    private final ProcessPayoutUseCase processPayoutUseCase;

    public AdminFinanceController(ProcessPayoutUseCase processPayoutUseCase) {
        this.processPayoutUseCase = processPayoutUseCase;
    }

    @GetMapping("/payouts/pending")
    public ApiResponse<List<PayoutResponse>> pendingPayouts() {
        return ApiResponse.ok(processPayoutUseCase.pending().stream().map(PayoutResponse::fromDomain).toList());
    }

    @PostMapping("/payouts/{payoutId}/complete")
    public ApiResponse<PayoutResponse> complete(@PathVariable String payoutId) {
        return ApiResponse.ok(PayoutResponse.fromDomain(processPayoutUseCase.complete(payoutId)));
    }

    @PostMapping("/payouts/{payoutId}/fail")
    public ApiResponse<PayoutResponse> fail(@PathVariable String payoutId) {
        return ApiResponse.ok(PayoutResponse.fromDomain(processPayoutUseCase.fail(payoutId)));
    }
}
