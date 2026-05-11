package com.vnshop.orderservice.infrastructure.admin;

import com.vnshop.orderservice.application.GetDashboardUseCase;
import com.vnshop.orderservice.domain.DashboardSummary;
import com.vnshop.orderservice.domain.RevenueTimeSeries;
import com.vnshop.orderservice.domain.TopItem;
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
    public ApiResponse<DashboardSummary> summary() {
        return ApiResponse.ok(getDashboardUseCase.summary());
    }

    @GetMapping("/revenue")
    public ApiResponse<RevenueTimeSeries> revenue() {
        return ApiResponse.ok(getDashboardUseCase.revenue());
    }

    @GetMapping("/top-products")
    public ApiResponse<List<TopItem>> topProducts() {
        return ApiResponse.ok(getDashboardUseCase.topProducts());
    }

    @GetMapping("/top-sellers")
    public ApiResponse<List<TopItem>> topSellers() {
        return ApiResponse.ok(getDashboardUseCase.topSellers());
    }
}
