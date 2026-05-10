package com.vnshop.cartservice.infrastructure.web;

import com.vnshop.cartservice.application.AddToCartUseCase;
import com.vnshop.cartservice.application.ClearCartUseCase;
import com.vnshop.cartservice.application.RemoveItemUseCase;
import com.vnshop.cartservice.application.UpdateQuantityUseCase;
import com.vnshop.cartservice.application.ViewCartUseCase;
import com.vnshop.cartservice.domain.Cart;
import com.vnshop.cartservice.domain.CartItem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequestMapping("/cart")
public class CartController {
    private static final String BUYER_ID_HEADER = "X-Buyer-Id";

    private final AddToCartUseCase addToCartUseCase;
    private final ViewCartUseCase viewCartUseCase;
    private final UpdateQuantityUseCase updateQuantityUseCase;
    private final RemoveItemUseCase removeItemUseCase;
    private final ClearCartUseCase clearCartUseCase;

    public CartController(AddToCartUseCase addToCartUseCase, ViewCartUseCase viewCartUseCase,
            UpdateQuantityUseCase updateQuantityUseCase, RemoveItemUseCase removeItemUseCase,
            ClearCartUseCase clearCartUseCase) {
        this.addToCartUseCase = addToCartUseCase;
        this.viewCartUseCase = viewCartUseCase;
        this.updateQuantityUseCase = updateQuantityUseCase;
        this.removeItemUseCase = removeItemUseCase;
        this.clearCartUseCase = clearCartUseCase;
    }

    @GetMapping
    public Cart view(@RequestHeader(BUYER_ID_HEADER) String buyerId) {
        return viewCartUseCase.view(buyerId);
    }

    @PostMapping("/items")
    public Cart addItem(@RequestHeader(BUYER_ID_HEADER) String buyerId, @Valid @RequestBody AddItemRequest request) {
        return addToCartUseCase.add(buyerId, request.toCartItem());
    }

    @PutMapping("/items")
    public Cart updateQuantity(@RequestHeader(BUYER_ID_HEADER) String buyerId,
            @Valid @RequestBody UpdateQuantityRequest request) {
        return updateQuantityUseCase.update(buyerId, request.productId(), request.variantSku(), request.quantity());
    }

    @DeleteMapping("/items/{productId}/{variantSku}")
    public Cart removeItem(@RequestHeader(BUYER_ID_HEADER) String buyerId, @PathVariable String productId,
            @PathVariable String variantSku) {
        return removeItemUseCase.remove(buyerId, productId, variantSku);
    }

    @DeleteMapping
    public ResponseEntity<Cart> clear(@RequestHeader(BUYER_ID_HEADER) String buyerId) {
        return ResponseEntity.ok(clearCartUseCase.clear(buyerId));
    }

    public record AddItemRequest(
            @NotBlank String productId,
            @NotBlank String variantSku,
            @NotBlank String name,
            @Min(1) @Max(99) int quantity,
            @NotNull BigDecimal unitPrice,
            String imageUrl) {
        CartItem toCartItem() {
            return new CartItem(productId, variantSku, name, quantity, unitPrice, imageUrl);
        }
    }

    public record UpdateQuantityRequest(
            @NotBlank String productId,
            @NotBlank String variantSku,
            @Min(0) @Max(99) int quantity) {
    }
}
