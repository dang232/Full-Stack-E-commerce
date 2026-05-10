package com.vnshop.orderservice.infrastructure.admin;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/dashboard")
public class AdminDashboardController {
    private final DashboardService dashboardService;

    public AdminDashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    public DashboardSummary summary() {
        return dashboardService.summary();
    }

    @GetMapping("/revenue")
    public RevenueTimeSeries revenue() {
        return dashboardService.revenue();
    }

    @GetMapping("/top-products")
    public List<TopItem> topProducts() {
        return dashboardService.topProducts();
    }

    @GetMapping("/top-sellers")
    public List<TopItem> topSellers() {
        return dashboardService.topSellers();
    }
}
