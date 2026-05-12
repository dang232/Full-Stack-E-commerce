package com.vnshop.orderservice.infrastructure.admin;

import com.vnshop.orderservice.application.GetDashboardUseCase;
import com.vnshop.orderservice.infrastructure.web.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/dashboard")
public class AdminDashboardController {
    private final GetDashboardUseCase getDashboardUseCase;

    public AdminDashboardController(GetDashboardUseCase getDashboardUseCase) {
        this.getDashboardUseCase = getDashboardUseCase;
    }

    @GetMapping("/summary")
    public ApiResponse<DashboardSummaryResponse> summary() {
        return ApiResponse.ok(DashboardSummaryResponse.fromDomain(getDashboardUseCase.summary()));
    }

    @GetMapping("/revenue")
    public ApiResponse<RevenueTimeSeriesResponse> revenue() {
        return ApiResponse.ok(RevenueTimeSeriesResponse.fromDomain(getDashboardUseCase.revenue()));
    }

    @GetMapping("/top-products")
    public ApiResponse<List<TopItemResponse>> topProducts() {
        return ApiResponse.ok(getDashboardUseCase.topProducts().stream().map(TopItemResponse::fromDomain).toList());
    }

    @GetMapping("/top-sellers")
    public ApiResponse<List<TopItemResponse>> topSellers() {
        return ApiResponse.ok(getDashboardUseCase.topSellers().stream().map(TopItemResponse::fromDomain).toList());
    }
}
