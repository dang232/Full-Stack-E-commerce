package com.vnshop.orderservice.infrastructure.web;

import com.vnshop.orderservice.application.AdminOrderUseCase;
import com.vnshop.orderservice.domain.projection.OrderSummaryProjection;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/admin/orders")
@PreAuthorize("hasRole('ADMIN')")
public class AdminOrderController {

    private final AdminOrderUseCase adminOrderUseCase;

    public AdminOrderController(AdminOrderUseCase adminOrderUseCase) {
        this.adminOrderUseCase = adminOrderUseCase;
    }

    @GetMapping
    public ApiResponse<List<OrderSummaryProjection>> listOrders(
            @RequestParam(required = false) String status
    ) {
        return ApiResponse.ok(adminOrderUseCase.listAllOrders(status));
    }

    @GetMapping("/by-buyer/{buyerId}")
    public ApiResponse<List<OrderSummaryProjection>> listOrdersByBuyer(@PathVariable String buyerId) {
        return ApiResponse.ok(adminOrderUseCase.listOrdersByBuyer(buyerId));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<OrderResponse> cancel(@PathVariable UUID id) {
        return ApiResponse.ok(OrderResponse.fromDomain(adminOrderUseCase.forceCancel(id)));
    }

    @PostMapping("/{id}/refund")
    public ApiResponse<OrderResponse> refund(@PathVariable UUID id) {
        return ApiResponse.ok(OrderResponse.fromDomain(adminOrderUseCase.forceRefund(id)));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<OrderResponse> changeStatus(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body
    ) {
        String status = body.get("status");
        if (status == null || status.isBlank()) {
            return ApiResponse.error("status field is required", "VALIDATION_ERROR");
        }
        return ApiResponse.ok(OrderResponse.fromDomain(adminOrderUseCase.changeStatus(id, status)));
    }
}
