package com.vnshop.userservice.infrastructure.web;

import com.vnshop.userservice.application.AdminUserUseCase;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserUseCase adminUserUseCase;

    public AdminUserController(AdminUserUseCase adminUserUseCase) {
        this.adminUserUseCase = adminUserUseCase;
    }

    @GetMapping
    public ApiResponse<List<BuyerProfileResponse>> searchUsers(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String phone
    ) {
        return ApiResponse.ok(
                adminUserUseCase.searchUsers(email, phone).stream()
                        .map(BuyerProfileResponse::fromDomain)
                        .toList()
        );
    }

    @PostMapping("/{id}/ban")
    public ApiResponse<BuyerProfileResponse> ban(@PathVariable String id) {
        return ApiResponse.ok(BuyerProfileResponse.fromDomain(adminUserUseCase.banUser(id)));
    }

    @PostMapping("/{id}/unban")
    public ApiResponse<BuyerProfileResponse> unban(@PathVariable String id) {
        return ApiResponse.ok(BuyerProfileResponse.fromDomain(adminUserUseCase.unbanUser(id)));
    }
}
