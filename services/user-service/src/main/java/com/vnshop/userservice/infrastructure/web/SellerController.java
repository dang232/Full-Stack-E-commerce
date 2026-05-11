package com.vnshop.userservice.infrastructure.web;

import com.vnshop.userservice.application.RegisterSellerCommand;
import com.vnshop.userservice.application.RegisterSellerUseCase;
import com.vnshop.userservice.application.ViewSellerProfileUseCase;
import com.vnshop.userservice.infrastructure.config.JwtPrincipalUtil;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/sellers")
public class SellerController {
    private final RegisterSellerUseCase registerSellerUseCase;
    private final ViewSellerProfileUseCase viewSellerProfileUseCase;

    public SellerController(RegisterSellerUseCase registerSellerUseCase, ViewSellerProfileUseCase viewSellerProfileUseCase) {
        this.registerSellerUseCase = registerSellerUseCase;
        this.viewSellerProfileUseCase = viewSellerProfileUseCase;
    }

    @PostMapping("/register")
    public ApiResponse<SellerProfileResponse> register(@RequestBody RegisterSellerRequest request) {
        var sellerProfile = registerSellerUseCase.register(new RegisterSellerCommand(
                JwtPrincipalUtil.currentUserId(),
                request.shopName(),
                request.bankName(),
                request.bankAccount()
        ));
        return ApiResponse.ok(SellerProfileResponse.fromDomain(sellerProfile));
    }

    @GetMapping("/me")
    public ApiResponse<SellerProfileResponse> getMySellerProfile() {
        return ApiResponse.ok(SellerProfileResponse.fromDomain(viewSellerProfileUseCase.view(JwtPrincipalUtil.currentUserId())));
    }
}
