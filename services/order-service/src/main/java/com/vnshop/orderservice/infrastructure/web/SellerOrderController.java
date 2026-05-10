package com.vnshop.orderservice.infrastructure.web;

import com.vnshop.orderservice.application.AcceptOrderUseCase;
import com.vnshop.orderservice.application.RejectOrderUseCase;
import com.vnshop.orderservice.application.ShipOrderUseCase;
import com.vnshop.orderservice.domain.FulfillmentStatus;
import com.vnshop.orderservice.infrastructure.persistence.OrderJpaRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/seller/orders")
public class SellerOrderController {
    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String SELLER_ID_HEADER = "X-Seller-Id";

    private final OrderJpaRepository orderJpaRepository;
    private final AcceptOrderUseCase acceptOrderUseCase;
    private final RejectOrderUseCase rejectOrderUseCase;
    private final ShipOrderUseCase shipOrderUseCase;

    public SellerOrderController(
            OrderJpaRepository orderJpaRepository,
            AcceptOrderUseCase acceptOrderUseCase,
            RejectOrderUseCase rejectOrderUseCase,
            ShipOrderUseCase shipOrderUseCase
    ) {
        this.orderJpaRepository = orderJpaRepository;
        this.acceptOrderUseCase = acceptOrderUseCase;
        this.rejectOrderUseCase = rejectOrderUseCase;
        this.shipOrderUseCase = shipOrderUseCase;
    }

    @GetMapping("/pending")
    public List<OrderController.OrderResponse> pending(
            @RequestHeader(name = USER_ID_HEADER, required = false) String userId,
            @RequestHeader(name = SELLER_ID_HEADER, required = false) String sellerId
    ) {
        String currentSellerId = currentSellerId(userId, sellerId);
        return orderJpaRepository.findBySellerIdAndFulfillmentStatus(currentSellerId, FulfillmentStatus.PENDING_ACCEPTANCE).stream()
                .map(OrderController.OrderResponse::fromDomain)
                .toList();
    }

    @PutMapping("/{subOrderId}/accept")
    public OrderController.OrderResponse accept(
            @PathVariable Long subOrderId,
            @RequestHeader(name = USER_ID_HEADER, required = false) String userId,
            @RequestHeader(name = SELLER_ID_HEADER, required = false) String sellerId
    ) {
        return OrderController.OrderResponse.fromDomain(acceptOrderUseCase.accept(orderIdFromSubOrderId(subOrderId), currentSellerId(userId, sellerId)));
    }

    @PutMapping("/{subOrderId}/reject")
    public OrderController.OrderResponse reject(
            @PathVariable Long subOrderId,
            @RequestHeader(name = USER_ID_HEADER, required = false) String userId,
            @RequestHeader(name = SELLER_ID_HEADER, required = false) String sellerId
    ) {
        return OrderController.OrderResponse.fromDomain(rejectOrderUseCase.reject(orderIdFromSubOrderId(subOrderId), currentSellerId(userId, sellerId)));
    }

    @PutMapping("/{subOrderId}/ship")
    public OrderController.OrderResponse ship(
            @PathVariable Long subOrderId,
            @RequestHeader(name = USER_ID_HEADER, required = false) String userId,
            @RequestHeader(name = SELLER_ID_HEADER, required = false) String sellerId,
            @Valid @RequestBody ShipRequest request
    ) {
        return OrderController.OrderResponse.fromDomain(shipOrderUseCase.ship(orderIdFromSubOrderId(subOrderId), currentSellerId(userId, sellerId), request.carrier(), request.trackingNumber()));
    }

    private String orderIdFromSubOrderId(Long subOrderId) {
        return orderJpaRepository.findOrderIdBySubOrderId(subOrderId)
                .orElseThrow(() -> new IllegalArgumentException("subOrder not found: " + subOrderId));
    }

    private static String currentSellerId(String userId, String sellerId) {
        if (sellerId != null && !sellerId.isBlank()) {
            return sellerId;
        }
        if (userId != null && !userId.isBlank()) {
            return userId;
        }
        throw new IllegalArgumentException("seller id is required");
    }

    public record ShipRequest(@NotBlank String carrier, @NotBlank String trackingNumber) {
    }
}
