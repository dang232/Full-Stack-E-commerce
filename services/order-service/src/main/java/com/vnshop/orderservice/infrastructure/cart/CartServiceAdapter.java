package com.vnshop.orderservice.infrastructure.cart;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vnshop.orderservice.domain.checkout.CartItemSnapshot;
import com.vnshop.orderservice.domain.checkout.CartSnapshot;
import com.vnshop.orderservice.domain.port.out.CartRepositoryPort;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class CartServiceAdapter implements CartRepositoryPort {

    private final CartHttpClient cartHttpClient;
    private final ObjectMapper objectMapper;
    private final CircuitBreaker circuitBreaker;

    public CartServiceAdapter(
            CartHttpClient cartHttpClient,
            ObjectMapper objectMapper,
            @Qualifier("cartServiceCircuitBreaker") CircuitBreaker circuitBreaker) {
        this.cartHttpClient = cartHttpClient;
        this.objectMapper = objectMapper;
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public CartSnapshot findByCartId(String cartId) {
        try {
            return circuitBreaker.executeSupplier(() -> fetchCart(cartId));
        } catch (CallNotPermittedException e) {
            throw new CartUnavailableException(
                    "Cart service circuit breaker is OPEN — refusing call until recovery window elapses", e);
        }
    }

    private CartSnapshot fetchCart(String cartId) {
        try {
            String json = cartHttpClient.getCart(cartId);

            if (json == null) {
                return new CartSnapshot(cartId, List.of());
            }

            ApiResponseDto apiResponse = objectMapper.readValue(json, ApiResponseDto.class);
            if (apiResponse == null || apiResponse.data == null || apiResponse.data.items == null) {
                return new CartSnapshot(cartId, List.of());
            }

            List<CartItemSnapshot> items = apiResponse.data.items.stream()
                    .map(dto -> new CartItemSnapshot(
                            dto.productId,
                            "", // variantSku not provided by cart-service
                            dto.productName,
                            dto.quantity,
                            new BigDecimal(String.valueOf(dto.unitPrice.amount))))
                    .collect(Collectors.toList());

            return new CartSnapshot(cartId, items);

        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                // 404 is "no cart yet" — a normal empty result, not a CB-tripping failure.
                return new CartSnapshot(cartId, List.of());
            }
            throw new CartUnavailableException(
                    "Cart service returned " + e.getStatusCode() + ": " + e.getResponseBodyAsString(), e);
        } catch (ResourceAccessException e) {
            throw new CartUnavailableException(
                    "Cart service unreachable or timed out: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new CartUnavailableException("Failed to read cart-service response: " + e.getMessage(), e);
        }
    }

    // --- JSON DTO inner classes matching cart-service ApiResponse<CartResponse> shape ---

    private static class ApiResponseDto {
        @JsonProperty("success")
        public boolean success;

        @JsonProperty("message")
        public String message;

        @JsonProperty("data")
        public CartResponseDto data;

        @JsonProperty("errorCode")
        public String errorCode;

        @JsonProperty("timestamp")
        public String timestamp;
    }

    private static class CartResponseDto {
        @JsonProperty("userId")
        public String userId;

        @JsonProperty("items")
        public List<CartItemResponseDto> items;

        @JsonProperty("itemCount")
        public int itemCount;

        @JsonProperty("uniqueItemCount")
        public int uniqueItemCount;

        @JsonProperty("totalAmount")
        public MoneyResponseDto totalAmount;

        @JsonProperty("updatedAt")
        public String updatedAt;
    }

    private static class CartItemResponseDto {
        @JsonProperty("productId")
        public String productId;

        @JsonProperty("productName")
        public String productName;

        @JsonProperty("productImage")
        public String productImage;

        @JsonProperty("unitPrice")
        public MoneyResponseDto unitPrice;

        @JsonProperty("quantity")
        public int quantity;

        @JsonProperty("subtotal")
        public MoneyResponseDto subtotal;

        @JsonProperty("addedAt")
        public String addedAt;
    }

    private static class MoneyResponseDto {
        @JsonProperty("amount")
        public double amount;

        @JsonProperty("currency")
        public String currency;
    }
}
