package com.vnshop.userservice.infrastructure.web;

import com.vnshop.userservice.application.RegisterSellerUseCase;
import com.vnshop.userservice.domain.SellerProfile;
import com.vnshop.userservice.domain.port.out.UserRepositoryPort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/sellers")
public class SellerController {
    private final RegisterSellerUseCase registerSellerUseCase;
    private final UserRepositoryPort userRepositoryPort;

    public SellerController(RegisterSellerUseCase registerSellerUseCase, UserRepositoryPort userRepositoryPort) {
        this.registerSellerUseCase = registerSellerUseCase;
        this.userRepositoryPort = userRepositoryPort;
    }

    @PostMapping("/register")
    public SellerProfileResponse register(@RequestBody RegisterSellerRequest request) {
        SellerProfile sellerProfile = registerSellerUseCase.register(
                SecurityKeycloakId.current(),
                request.shopName(),
                request.bankName(),
                request.bankAccount()
        );
        return SellerProfileResponse.fromDomain(sellerProfile);
    }

    @GetMapping("/me")
    public SellerProfileResponse getMySellerProfile() {
        return userRepositoryPort.findSellerById(SecurityKeycloakId.current())
                .map(SellerProfileResponse::fromDomain)
                .orElseThrow(() -> new IllegalArgumentException("seller profile not found"));
    }

    public record RegisterSellerRequest(String shopName, String bankName, String bankAccount) {
    }

    public record SellerProfileResponse(String id, String shopName, String bankName, String bankAccount, boolean approved, String tier, boolean vacationMode) {
        static SellerProfileResponse fromDomain(SellerProfile sellerProfile) {
            return new SellerProfileResponse(
                    sellerProfile.id(),
                    sellerProfile.shopName(),
                    sellerProfile.bankName(),
                    sellerProfile.bankAccount(),
                    sellerProfile.approved(),
                    sellerProfile.tier().name(),
                    sellerProfile.vacationMode()
            );
        }
    }
}
