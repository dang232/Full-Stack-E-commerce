package com.vnshop.orderservice.infrastructure.web;

import com.vnshop.orderservice.application.CancelOrderCommand;
import com.vnshop.orderservice.application.CancelOrderUseCase;
import com.vnshop.orderservice.application.CreateOrderCommand;
import com.vnshop.orderservice.application.CreateOrderUseCase;
import com.vnshop.orderservice.application.ViewOrderUseCase;
import com.vnshop.orderservice.application.query.OrderQueryHandler;
import com.vnshop.orderservice.infrastructure.config.JwtPrincipalUtil;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/orders")
public class OrderController {
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final CreateOrderUseCase createOrderUseCase;
    private final CancelOrderUseCase cancelOrderUseCase;
    private final OrderQueryHandler orderQueryHandler;
    private final ViewOrderUseCase viewOrderUseCase;

    public OrderController(CreateOrderUseCase createOrderUseCase, CancelOrderUseCase cancelOrderUseCase,
            OrderQueryHandler orderQueryHandler, ViewOrderUseCase viewOrderUseCase) {
        this.createOrderUseCase = createOrderUseCase;
        this.cancelOrderUseCase = cancelOrderUseCase;
        this.orderQueryHandler = orderQueryHandler;
        this.viewOrderUseCase = viewOrderUseCase;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OrderResponse> checkout(
            @RequestHeader(name = IDEMPOTENCY_KEY_HEADER) String idempotencyKey,
            @Valid @RequestBody CheckoutRequest request
    ) {
        return ApiResponse.ok(OrderResponse.fromDomain(createOrderUseCase.create(new CreateOrderCommand(JwtPrincipalUtil.currentUserId(), request.shippingAddress().toDomain(), request.toItems(), idempotencyKey))));
    }

    @GetMapping
    public ApiResponse<List<OrderListItemResponse>> list() {
        return ApiResponse.ok(orderQueryHandler.findByBuyerId(JwtPrincipalUtil.currentUserId()).stream()
                .map(OrderListItemResponse::fromProjection).toList());
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(OrderResponse.fromDomain(viewOrderUseCase.view(id)));
    }

    @DeleteMapping("/{id}/cancel")
    public ApiResponse<OrderResponse> cancel(@PathVariable UUID id) {
        return ApiResponse.ok(OrderResponse.fromDomain(cancelOrderUseCase.cancel(new CancelOrderCommand(id, JwtPrincipalUtil.currentUserId()))));
    }

}
