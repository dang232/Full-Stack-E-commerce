package com.vnshop.orderservice.infrastructure.web;

import com.vnshop.orderservice.application.CancelOrderUseCase;
import com.vnshop.orderservice.application.CreateOrderUseCase;
import com.vnshop.orderservice.domain.Address;
import com.vnshop.orderservice.domain.Money;
import com.vnshop.orderservice.domain.Order;
import com.vnshop.orderservice.domain.OrderItem;
import com.vnshop.orderservice.domain.SubOrder;
import com.vnshop.orderservice.domain.port.out.OrderRepositoryPort;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/orders")
public class OrderController {
    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String BUYER_ID_HEADER = "X-Buyer-Id";
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final CreateOrderUseCase createOrderUseCase;
    private final CancelOrderUseCase cancelOrderUseCase;
    private final OrderRepositoryPort orderRepositoryPort;

    public OrderController(CreateOrderUseCase createOrderUseCase, CancelOrderUseCase cancelOrderUseCase, OrderRepositoryPort orderRepositoryPort) {
        this.createOrderUseCase = createOrderUseCase;
        this.cancelOrderUseCase = cancelOrderUseCase;
        this.orderRepositoryPort = orderRepositoryPort;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse checkout(
            @RequestHeader(name = USER_ID_HEADER, required = false) String userId,
            @RequestHeader(name = BUYER_ID_HEADER, required = false) String buyerId,
            @RequestHeader(name = IDEMPOTENCY_KEY_HEADER) String idempotencyKey,
            @Valid @RequestBody CheckoutRequest request
    ) {
        return OrderResponse.fromDomain(createOrderUseCase.create(currentBuyerId(userId, buyerId), request.shippingAddress().toDomain(), request.toItems(), idempotencyKey));
    }

    @GetMapping
    public List<OrderResponse> list(
            @RequestHeader(name = USER_ID_HEADER, required = false) String userId,
            @RequestHeader(name = BUYER_ID_HEADER, required = false) String buyerId
    ) {
        return orderRepositoryPort.findByBuyerId(currentBuyerId(userId, buyerId)).stream().map(OrderResponse::fromDomain).toList();
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable String id) {
        return orderRepositoryPort.findById(id)
                .map(OrderResponse::fromDomain)
                .orElseThrow(() -> new IllegalArgumentException("order not found: " + id));
    }

    @DeleteMapping("/{id}/cancel")
    public OrderResponse cancel(
            @PathVariable String id,
            @RequestHeader(name = USER_ID_HEADER, required = false) String userId,
            @RequestHeader(name = BUYER_ID_HEADER, required = false) String buyerId
    ) {
        return OrderResponse.fromDomain(cancelOrderUseCase.cancel(id, currentBuyerId(userId, buyerId)));
    }

    private static String currentBuyerId(String userId, String buyerId) {
        if (buyerId != null && !buyerId.isBlank()) {
            return buyerId;
        }
        if (userId != null && !userId.isBlank()) {
            return userId;
        }
        throw new IllegalArgumentException("buyer id is required");
    }

    public record CheckoutRequest(@Valid @NotNull AddressRequest shippingAddress, @Valid @NotEmpty List<OrderItemRequest> items) {
        List<OrderItem> toItems() {
            return items.stream().map(OrderItemRequest::toDomain).toList();
        }
    }

    public record AddressRequest(@NotBlank String street, String ward, @NotBlank String district, @NotBlank String city) {
        Address toDomain() {
            return new Address(street, ward, district, city);
        }
    }

    public record OrderItemRequest(
            @NotBlank String productId,
            @NotBlank String variantSku,
            @NotBlank String sellerId,
            @NotBlank String name,
            @Min(1) int quantity,
            @NotNull BigDecimal unitPriceAmount,
            String unitPriceCurrency,
            String imageUrl
    ) {
        OrderItem toDomain() {
            return new OrderItem(productId, variantSku, sellerId, name, quantity, new Money(unitPriceAmount, unitPriceCurrency), imageUrl);
        }
    }

    public record OrderResponse(
            String id,
            String orderNumber,
            String buyerId,
            AddressResponse shippingAddress,
            List<SubOrderResponse> subOrders,
            MoneyResponse itemsTotal,
            MoneyResponse shippingTotal,
            MoneyResponse discount,
            MoneyResponse finalAmount,
            String paymentMethod,
            String paymentStatus,
            String idempotencyKey
    ) {
        static OrderResponse fromDomain(Order order) {
            return new OrderResponse(
                    order.id(),
                    order.orderNumber(),
                    order.buyerId(),
                    AddressResponse.fromDomain(order.shippingAddress()),
                    order.subOrders().stream().map(SubOrderResponse::fromDomain).toList(),
                    MoneyResponse.fromDomain(order.itemsTotal()),
                    MoneyResponse.fromDomain(order.shippingTotal()),
                    MoneyResponse.fromDomain(order.discount()),
                    MoneyResponse.fromDomain(order.finalAmount()),
                    order.paymentMethod(),
                    order.paymentStatus().name(),
                    order.idempotencyKey()
            );
        }
    }

    public record SubOrderResponse(
            Long subOrderId,
            String sellerId,
            String fulfillmentStatus,
            MoneyResponse shippingCost,
            String shippingMethod,
            String carrier,
            String trackingNumber,
            List<OrderItemResponse> items
    ) {
        static SubOrderResponse fromDomain(SubOrder subOrder) {
            return new SubOrderResponse(
                    subOrder.id(),
                    subOrder.sellerId(),
                    subOrder.fulfillmentStatus().name(),
                    MoneyResponse.fromDomain(subOrder.shippingCost()),
                    subOrder.shippingMethod(),
                    subOrder.carrier(),
                    subOrder.trackingNumber(),
                    subOrder.items().stream().map(OrderItemResponse::fromDomain).toList()
            );
        }
    }

    public record OrderItemResponse(String productId, String variantSku, String sellerId, String name, int quantity, MoneyResponse unitPrice, String imageUrl) {
        static OrderItemResponse fromDomain(OrderItem item) {
            return new OrderItemResponse(item.productId(), item.variantSku(), item.sellerId(), item.name(), item.quantity(), MoneyResponse.fromDomain(item.unitPrice()), item.imageUrl());
        }
    }

    public record AddressResponse(String street, String ward, String district, String city) {
        static AddressResponse fromDomain(Address address) {
            return new AddressResponse(address.street(), address.ward(), address.district(), address.city());
        }
    }

    public record MoneyResponse(BigDecimal amount, String currency) {
        static MoneyResponse fromDomain(Money money) {
            return new MoneyResponse(money.amount(), money.currency());
        }
    }
}
