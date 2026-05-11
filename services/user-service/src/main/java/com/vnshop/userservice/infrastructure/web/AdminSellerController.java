package com.vnshop.userservice.infrastructure.web;

import com.vnshop.userservice.application.ApproveSellerUseCase;
import com.vnshop.userservice.application.ListPendingSellersUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/sellers")
public class AdminSellerController {
    private final ListPendingSellersUseCase listPendingSellersUseCase;
    private final ApproveSellerUseCase approveSellerUseCase;

    public AdminSellerController(ListPendingSellersUseCase listPendingSellersUseCase, ApproveSellerUseCase approveSellerUseCase) {
        this.listPendingSellersUseCase = listPendingSellersUseCase;
        this.approveSellerUseCase = approveSellerUseCase;
    }

    @GetMapping
    public ApiResponse<List<SellerProfileResponse>> pendingSellers() {
        return ApiResponse.ok(listPendingSellersUseCase.listPending().stream()
                .map(SellerProfileResponse::fromDomain)
                .toList());
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<SellerProfileResponse> approve(@PathVariable String id) {
        return ApiResponse.ok(SellerProfileResponse.fromDomain(approveSellerUseCase.approve(id)));
    }
}
