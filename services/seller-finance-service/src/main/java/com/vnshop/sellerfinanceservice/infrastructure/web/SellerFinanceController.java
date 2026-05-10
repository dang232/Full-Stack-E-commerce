package com.vnshop.sellerfinanceservice.infrastructure.web;

import com.vnshop.sellerfinanceservice.application.CreditWalletUseCase;
import com.vnshop.sellerfinanceservice.application.RequestPayoutUseCase;
import com.vnshop.sellerfinanceservice.domain.CommissionTier;
import com.vnshop.sellerfinanceservice.domain.Payout;
import com.vnshop.sellerfinanceservice.domain.SellerWallet;
import com.vnshop.sellerfinanceservice.domain.port.out.PayoutRepositoryPort;
import com.vnshop.sellerfinanceservice.domain.port.out.SellerWalletRepositoryPort;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/sellers/me/finance")
public class SellerFinanceController {
    private static final String DEFAULT_SELLER_ID = "stub-seller";

    private final SellerWalletRepositoryPort walletRepository;
    private final PayoutRepositoryPort payoutRepository;
    private final CreditWalletUseCase creditWalletUseCase;
    private final RequestPayoutUseCase requestPayoutUseCase;

    public SellerFinanceController(
            SellerWalletRepositoryPort walletRepository,
            PayoutRepositoryPort payoutRepository,
            CreditWalletUseCase creditWalletUseCase,
            RequestPayoutUseCase requestPayoutUseCase
    ) {
        this.walletRepository = walletRepository;
        this.payoutRepository = payoutRepository;
        this.creditWalletUseCase = creditWalletUseCase;
        this.requestPayoutUseCase = requestPayoutUseCase;
    }

    @GetMapping("/wallet")
    public WalletResponse wallet(@RequestHeader(name = "X-Seller-Id", required = false) String sellerId) {
        SellerWallet wallet = walletRepository.findBySellerId(currentSellerId(sellerId)).orElseGet(() -> new SellerWallet(currentSellerId(sellerId)));
        return WalletResponse.fromDomain(wallet);
    }

    @GetMapping("/payouts")
    public List<PayoutResponse> payouts(@RequestHeader(name = "X-Seller-Id", required = false) String sellerId) {
        return payoutRepository.findBySellerId(currentSellerId(sellerId)).stream().map(PayoutResponse::fromDomain).toList();
    }

    @PostMapping("/payouts")
    @ResponseStatus(HttpStatus.CREATED)
    public PayoutResponse requestPayout(@RequestHeader(name = "X-Seller-Id", required = false) String sellerId, @Valid @RequestBody PayoutRequest request) {
        return PayoutResponse.fromDomain(requestPayoutUseCase.request(currentSellerId(sellerId), request.amount()));
    }

    @PostMapping("/credits")
    @ResponseStatus(HttpStatus.CREATED)
    public CreditResponse credit(@RequestHeader(name = "X-Seller-Id", required = false) String sellerId, @Valid @RequestBody CreditRequest request) {
        CreditWalletUseCase.CreditWalletResult result = creditWalletUseCase.credit(currentSellerId(sellerId), request.orderAmount(), request.tier());
        return new CreditResponse(WalletResponse.fromDomain(result.wallet()), result.commission(), result.sellerNet());
    }

    private static String currentSellerId(String sellerId) {
        return sellerId == null || sellerId.isBlank() ? DEFAULT_SELLER_ID : sellerId;
    }

    public record PayoutRequest(@NotNull @Positive BigDecimal amount) {
    }

    public record CreditRequest(@NotNull @Positive BigDecimal orderAmount, @NotNull CommissionTier tier) {
    }

    public record CreditResponse(WalletResponse wallet, BigDecimal commission, BigDecimal sellerNet) {
    }

    public record WalletResponse(String sellerId, BigDecimal availableBalance, BigDecimal pendingBalance, BigDecimal totalEarned, Instant lastPayoutAt) {
        static WalletResponse fromDomain(SellerWallet wallet) {
            return new WalletResponse(wallet.sellerId(), wallet.availableBalance(), wallet.pendingBalance(), wallet.totalEarned(), wallet.lastPayoutAt());
        }
    }

    public record PayoutResponse(String payoutId, String sellerId, BigDecimal amount, String status, Instant createdAt) {
        static PayoutResponse fromDomain(Payout payout) {
            return new PayoutResponse(payout.payoutId(), payout.sellerId(), payout.amount(), payout.status().name(), payout.createdAt());
        }
    }
}
