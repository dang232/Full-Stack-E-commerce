package com.vnshop.sellerfinanceservice.infrastructure.web;

import com.vnshop.sellerfinanceservice.application.ProcessPayoutUseCase;
import com.vnshop.sellerfinanceservice.domain.Payout;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/admin/finance")
public class AdminFinanceController {
    private final ProcessPayoutUseCase processPayoutUseCase;

    public AdminFinanceController(ProcessPayoutUseCase processPayoutUseCase) {
        this.processPayoutUseCase = processPayoutUseCase;
    }

    @GetMapping("/payouts/pending")
    public List<PayoutResponse> pendingPayouts() {
        return processPayoutUseCase.pending().stream().map(PayoutResponse::fromDomain).toList();
    }

    @PostMapping("/payouts/{payoutId}/complete")
    public PayoutResponse complete(@PathVariable String payoutId) {
        return PayoutResponse.fromDomain(processPayoutUseCase.complete(payoutId));
    }

    @PostMapping("/payouts/{payoutId}/fail")
    public PayoutResponse fail(@PathVariable String payoutId) {
        return PayoutResponse.fromDomain(processPayoutUseCase.fail(payoutId));
    }

    public record PayoutResponse(String payoutId, String sellerId, BigDecimal amount, String status, Instant createdAt) {
        static PayoutResponse fromDomain(Payout payout) {
            return new PayoutResponse(payout.payoutId(), payout.sellerId(), payout.amount(), payout.status().name(), payout.createdAt());
        }
    }
}
