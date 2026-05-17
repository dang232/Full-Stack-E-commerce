package com.vnshop.orderservice.infrastructure.web;

import com.vnshop.orderservice.application.GetSellerRevenueUseCase;
import com.vnshop.orderservice.domain.SellerRevenuePoint;
import com.vnshop.orderservice.infrastructure.config.JwtPrincipalUtil;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Per-seller analytics for the seller dashboard. Currently exposes a daily
 * revenue/order-count aggregate; admin already has the cross-seller view via
 * {@code AdminDashboardController}, this is the equivalent scoped to the
 * caller's own sellerId resolved from the JWT.
 */
@RestController
@RequestMapping("/sellers/me")
@Validated
public class SellerAnalyticsController {
    private static final int DEFAULT_DAYS = 30;

    private final GetSellerRevenueUseCase getSellerRevenueUseCase;

    public SellerAnalyticsController(GetSellerRevenueUseCase getSellerRevenueUseCase) {
        this.getSellerRevenueUseCase = getSellerRevenueUseCase;
    }

    @GetMapping("/revenue")
    public ApiResponse<List<SellerRevenuePointResponse>> revenue(
            @RequestParam(name = "days", required = false, defaultValue = "" + DEFAULT_DAYS)
            @Min(1) @Max(365) int days
    ) {
        String sellerId = JwtPrincipalUtil.currentSellerId();
        List<SellerRevenuePoint> points = getSellerRevenueUseCase.revenueForSeller(sellerId, days);
        return ApiResponse.ok(points.stream()
                .map(SellerRevenuePointResponse::fromDomain)
                .toList());
    }

    public record SellerRevenuePointResponse(LocalDate date, BigDecimal revenue, long orderCount) {
        static SellerRevenuePointResponse fromDomain(SellerRevenuePoint point) {
            return new SellerRevenuePointResponse(point.date(), point.revenue(), point.orderCount());
        }
    }
}
