package com.vnshop.orderservice.infrastructure.web.finance;

import com.vnshop.orderservice.application.finance.CreditWalletUseCase;
import com.vnshop.orderservice.application.finance.ListPayoutsUseCase;
import com.vnshop.orderservice.application.finance.RequestPayoutUseCase;
import com.vnshop.orderservice.application.finance.ViewWalletUseCase;
import com.vnshop.orderservice.infrastructure.config.JwtPrincipalUtil;
import com.vnshop.orderservice.infrastructure.web.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/sellers/me")
public class SellerFinanceController {
    private final ViewWalletUseCase viewWalletUseCase;
    private final ListPayoutsUseCase listPayoutsUseCase;
    private final CreditWalletUseCase creditWalletUseCase;
    private final RequestPayoutUseCase requestPayoutUseCase;

    public SellerFinanceController(
            ViewWalletUseCase viewWalletUseCase,
            ListPayoutsUseCase listPayoutsUseCase,
            CreditWalletUseCase creditWalletUseCase,
            RequestPayoutUseCase requestPayoutUseCase
    ) {
        this.viewWalletUseCase = viewWalletUseCase;
        this.listPayoutsUseCase = listPayoutsUseCase;
        this.creditWalletUseCase = creditWalletUseCase;
        this.requestPayoutUseCase = requestPayoutUseCase;
    }

    @GetMapping("/wallet")
    public ApiResponse<WalletResponse> wallet() {
        return ApiResponse.ok(WalletResponse.fromDomain(viewWalletUseCase.view(JwtPrincipalUtil.currentSellerId())));
    }

    @GetMapping("/payouts")
    public ApiResponse<List<PayoutResponse>> payouts() {
        return ApiResponse.ok(listPayoutsUseCase.listBySellerId(JwtPrincipalUtil.currentSellerId()).stream().map(PayoutResponse::fromDomain).toList());
    }

    @PostMapping("/payouts")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PayoutResponse> requestPayout(@Valid @RequestBody PayoutRequest request) {
        return ApiResponse.ok(PayoutResponse.fromDomain(requestPayoutUseCase.request(JwtPrincipalUtil.currentSellerId(), request.amount())));
    }

    @PostMapping("/credits")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CreditResponse> credit(@Valid @RequestBody CreditRequest request) {
        CreditWalletUseCase.CreditWalletResult result = creditWalletUseCase.credit(JwtPrincipalUtil.currentSellerId(), request.orderAmount(), request.tier(), request.idempotencyKey());
        return ApiResponse.ok(new CreditResponse(WalletResponse.fromDomain(result.wallet()), result.commission(), result.sellerNet()));
    }
}
