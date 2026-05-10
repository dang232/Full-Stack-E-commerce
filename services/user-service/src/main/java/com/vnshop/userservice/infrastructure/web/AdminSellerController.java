package com.vnshop.userservice.infrastructure.web;

import com.vnshop.userservice.application.ApproveSellerUseCase;
import com.vnshop.userservice.domain.port.out.UserRepositoryPort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/sellers")
public class AdminSellerController {
    private final UserRepositoryPort userRepositoryPort;
    private final ApproveSellerUseCase approveSellerUseCase;

    public AdminSellerController(UserRepositoryPort userRepositoryPort, ApproveSellerUseCase approveSellerUseCase) {
        this.userRepositoryPort = userRepositoryPort;
        this.approveSellerUseCase = approveSellerUseCase;
    }

    @GetMapping
    public List<SellerController.SellerProfileResponse> pendingSellers() {
        return userRepositoryPort.findPendingSellers().stream()
                .map(SellerController.SellerProfileResponse::fromDomain)
                .toList();
    }

    @PostMapping("/{id}/approve")
    public SellerController.SellerProfileResponse approve(@PathVariable String id) {
        return SellerController.SellerProfileResponse.fromDomain(approveSellerUseCase.approve(id));
    }
}
